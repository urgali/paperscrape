"""What the Kotlin sources actually do with each sprite, read from the sources.

The registry declares a sprite's scale convention, its tint class and its anchor.
None of those are properties a PNG records, so until this module existed the
declarations could only be compared against the code by reading it. Defect D-1 is
what that costs: a sprite's pixel size, its scale convention and its origin are
correct only together, `star_sparkle.png` was replaced by a 3x redraw while its
call site kept the raw-pixel convention, and nothing failed.

This module resolves a blit call site to a `(sprite, tint, scale, origin)` tuple
where it can, so `validate` can compare the declaration against it.

What it deliberately does not do
--------------------------------
It does not interpret Kotlin. There is no dataflow analysis here, and adding one
would be the wrong trade: a resolver that guesses is worse than one that admits
it cannot see, because a wrong answer is indistinguishable from a right one in
the report.

So resolution is syntactic and total-or-nothing per site:

* the sprite argument must be a literal ``R.drawable.<name>``. A call whose
  sprite comes from a variable or a lookup table -- every ``person_*`` sprite,
  and the moon's phase sprite -- yields an :class:`UnattributedSite` naming the
  expression, never a match against some sprite the resolver picked;
* the origin arguments must be numeric literals, or names declared as numeric
  ``const val`` in the same file;
* the scale must be a literal ``SpriteScale.<n>``, a ``val`` declared as one in
  the same file, or the binding of a wrapper function this module recognises.

Anything else leaves that field ``None``, and a ``None`` is reported as
unresolved. It is never reported as agreement.
"""

from __future__ import annotations

import re
from dataclasses import dataclass
from pathlib import Path

#: Blit entry points on `SpriteBlitter`, mapped to the tint class they imply and
#: the zero-based index of their `scale` argument. `draw` blits the bitmap's own
#: colours; `drawTinted` puts it through the runtime MULTIPLY filter.
BLITTER_METHODS = {
    "sprites.draw": ("FIXED_ART", 4),
    "sprites.drawTinted": ("TINTABLE", 4),
}

#: Argument index of `drawTinted`'s colour.
TINT_COLOUR_ARG = 5

#: Opaque white. Tinting multiplies, so white is the identity: a sprite blitted
#: through `drawTinted` with this colour keeps its own baked-in art exactly, and
#: is fixed art despite the entry point. The reason to route it through the
#: tinted path anyway is that `draw` takes no alpha argument, so a sprite that
#: fades needs `drawTinted` even when it is not recoloured.
IDENTITY_TINT = 0xFFFFFFFF

#: Argument index of the sprite resource and of the two origin coordinates. The
#: same for every entry point and for the wrappers, which forward them unchanged.
RESOURCE_ARG = 1
ORIGIN_X_ARG = 2
ORIGIN_Y_ARG = 3

_DRAWABLE = re.compile(r"^R\.drawable\.(\w+)$")
_FLOAT_LITERAL = re.compile(r"^-?\d+(?:\.\d+)?f$")
_SCALE_LITERAL = re.compile(r"^SpriteScale\.(\w+)$")
_IDENTIFIER = re.compile(r"^\w+$")
_COLOUR_LITERAL = re.compile(r"^0x([0-9A-Fa-f]+)(?:\.toInt\(\))?$")
_NUMERIC_CONST = re.compile(r"\bconst\s+val\s+(\w+)\s*=\s*(-?\d+(?:\.\d+)?)f\b")
_SCALE_CONST = re.compile(r"\bval\s+(\w+)\s*=\s*SpriteScale\.(\w+)\b")
_FUNCTION = re.compile(r"\bfun\s+(\w+)\s*\(")
_PARAMETER = re.compile(r"^(\w+)\s*:\s*([\w<>?]+)")


@dataclass(frozen=True)
class BlitSite:
    """One resolved blit of a named sprite."""

    sprite: str
    file: str
    tint: str
    scale: str | None
    origin: tuple[float, float] | None

    @property
    def unresolved_fields(self) -> tuple[str, ...]:
        missing = []
        if self.scale is None:
            missing.append("scale")
        if self.origin is None:
            missing.append("origin")
        return tuple(missing)


@dataclass(frozen=True)
class UnattributedSite:
    """A blit whose sprite argument is not a literal ``R.drawable.<name>``.

    Recorded rather than dropped: a sprite drawn only through sites like these
    has no evidence behind its declaration, and the count of such sites is the
    honest measure of how much of the asset set this check cannot reach.
    """

    file: str
    expression: str
    tint: str


def _strip_noise(text: str) -> str:
    """Blank out comments and string literals, preserving every offset.

    Replacing rather than deleting keeps line and column positions intact, so a
    span computed on the stripped text still points at the same place in the
    original.
    """
    out = list(text)
    i = 0
    n = len(text)
    while i < n:
        two = text[i : i + 2]
        if two == "//":
            j = text.find("\n", i)
            j = n if j < 0 else j
            for k in range(i, j):
                out[k] = " "
            i = j
        elif two == "/*":
            j = text.find("*/", i + 2)
            j = n if j < 0 else j + 2
            for k in range(i, j):
                if out[k] != "\n":
                    out[k] = " "
            i = j
        elif text[i] == '"':
            j = i + 1
            while j < n and text[j] != '"':
                j += 2 if text[j] == "\\" else 1
            j = min(j + 1, n)
            for k in range(i, j):
                if out[k] != "\n":
                    out[k] = " "
            i = j
        else:
            i += 1
    return "".join(out)


def _split_arguments(text: str, open_paren: int) -> tuple[list[str], int] | None:
    """Split one argument list, returning the arguments and the closing index.

    Depth-aware, so a nested call or an expression containing a comma stays one
    argument. Returns ``None`` for an unbalanced list rather than guessing where
    it ended.
    """
    depth = 0
    start = open_paren + 1
    args: list[str] = []
    for i in range(open_paren, len(text)):
        char = text[i]
        if char in "([{":
            depth += 1
        elif char in ")]}":
            depth -= 1
            if depth == 0:
                args.append(text[start:i])
                return [a.strip() for a in args], i
        elif char == "," and depth == 1:
            args.append(text[start:i])
            start = i + 1
    return None


def _find_calls(text: str, name: str) -> list[tuple[list[str], int, int]]:
    """Every call to ``name``, as (arguments, start offset, end offset)."""
    pattern = re.compile(rf"(?<![\w.]){re.escape(name)}\s*\(")
    found = []
    for match in pattern.finditer(text):
        # A declaration is not a call: `fun drawSprite(canvas: Canvas, …)` would
        # otherwise be read as a blit of a sprite named `resId: Int`.
        if text[:match.start()].rstrip().endswith("fun"):
            continue
        split = _split_arguments(text, match.end() - 1)
        if split is not None:
            args, end = split
            found.append((args, match.start(), end))
    return found


#: The types a wrapper's first parameter may have, in the order they arrived.
#:
#: This was the literal string ``"Canvas"``. The GPU migration changed both of
#: ``SceneObjectRenderer``'s wrappers to take ``SceneCanvas`` -- the abstraction the
#: renderer draws through, with the ``Canvas`` backend kept behind it -- and the
#: resolver stopped recognising them. It did so silently, because a file with no
#: recognised wrapper simply reports no wrapper: all sixty of that file's blit call
#: sites went unresolved with nothing failing, and the anchor declarations Phase 3.2
#: built this resolver to check went unverified for the file that carries most of
#: them. Recorded as defect D-4.
#:
#: A set rather than one more literal, because the same substitution can happen
#: again: what identifies a wrapper is that its first parameter is *the drawing
#: surface*, whichever type currently names one.
CANVAS_TYPES = frozenset({"Canvas", "SceneCanvas"})


def _wrapper_bindings(text: str) -> tuple[dict[str, tuple[str, str]], list[tuple[int, int]]]:
    """Wrapper functions that forward to a blitter method with a fixed scale.

    `SceneObjectRenderer` draws in one convention only and binds it in two
    one-line wrappers, so its call sites do not name a scale. The binding is read
    out of the wrapper body rather than assumed, which is what makes a change to
    the wrapper show up here instead of silently re-labelling sixty call sites.

    Returns the bindings and the body spans, so the forwarding call inside a
    wrapper is not itself mistaken for a call site.
    """
    bindings: dict[str, tuple[str, str]] = {}
    spans: list[tuple[int, int]] = []
    for match in _FUNCTION.finditer(text):
        name = match.group(1)
        split = _split_arguments(text, match.end() - 1)
        if split is None:
            continue
        parameters, signature_end = split
        names = [p.group(1) for p in map(_PARAMETER.match, parameters) if p]
        types = [p.group(2) for p in map(_PARAMETER.match, parameters) if p]
        # A wrapper is a function that forwards a caller's own sprite and origin
        # straight to a blitter method. Anything looser matches ordinary drawing
        # functions that merely happen to contain one blit, and swallowing their
        # bodies would silently drop real call sites.
        if len(names) < 4 or types[0] not in CANVAS_TYPES or types[1:4] != ["Int", "Float", "Float"]:
            continue
        body_start = text.find("{", signature_end)
        if body_start < 0:
            continue
        body_split = _split_arguments(text, body_start)
        if body_split is None:
            continue
        _, body_end = body_split
        body = text[body_start : body_end + 1]
        for method, (tint, scale_index) in BLITTER_METHODS.items():
            calls = _find_calls(body, method)
            if len(calls) != 1:
                continue
            args = calls[0][0]
            if len(args) <= scale_index or args[:4] != names[:4]:
                continue
            scale = _SCALE_LITERAL.match(args[scale_index])
            if scale:
                bindings[name] = (tint, scale.group(1))
                spans.append((body_start, body_end))
    return bindings, spans


def _resolve_origin(
    x_expr: str, y_expr: str, constants: dict[str, float]
) -> tuple[float, float] | None:
    values = []
    for expr in (x_expr, y_expr):
        if _FLOAT_LITERAL.match(expr):
            values.append(float(expr[:-1]))
        elif _IDENTIFIER.match(expr) and expr in constants:
            values.append(constants[expr])
        else:
            return None
    return values[0], values[1]


def _resolve_tint(expr: str, declared: str) -> str:
    """Refine a blit's tint class using the colour it was given.

    Only ``drawTinted`` reaches here, and only a literal colour changes the
    answer: an identity tint leaves the artwork untouched, so the sprite is fixed
    art whichever entry point drew it. A colour that is not a literal is a
    runtime value and therefore a real tint.
    """
    if declared != "TINTABLE":
        return declared
    literal = _COLOUR_LITERAL.match(expr)
    if literal and int(literal.group(1), 16) == IDENTITY_TINT:
        return "FIXED_ART"
    return declared


def _resolve_scale(expr: str, scale_constants: dict[str, str]) -> str | None:
    literal = _SCALE_LITERAL.match(expr)
    if literal:
        return literal.group(1)
    if _IDENTIFIER.match(expr) and expr in scale_constants:
        return scale_constants[expr]
    return None


def scan_file(path: Path) -> tuple[list[BlitSite], list[UnattributedSite]]:
    """Resolve every blit call site in one Kotlin file."""
    raw = path.read_text(encoding="utf-8")
    text = _strip_noise(raw)
    constants = {m.group(1): float(m.group(2)) for m in _NUMERIC_CONST.finditer(text)}
    scale_constants = {m.group(1): m.group(2) for m in _SCALE_CONST.finditer(text)}
    bindings, wrapper_spans = _wrapper_bindings(text)

    entry_points: dict[str, tuple[str, int | None]] = {
        method: (tint, index) for method, (tint, index) in BLITTER_METHODS.items()
    }
    for wrapper, (tint, _) in bindings.items():
        entry_points[wrapper] = (tint, None)

    sites: list[BlitSite] = []
    unattributed: list[UnattributedSite] = []
    for name, (tint, scale_index) in entry_points.items():
        for args, start, _ in _find_calls(text, name):
            if any(low <= start <= high for low, high in wrapper_spans):
                continue  # the wrapper's own forwarding call, already consumed
            if len(args) <= ORIGIN_Y_ARG:
                continue
            resource = _DRAWABLE.match(args[RESOURCE_ARG])
            if not resource:
                unattributed.append(
                    UnattributedSite(
                        file=path.name, expression=args[RESOURCE_ARG], tint=tint
                    )
                )
                continue
            if scale_index is None:
                scale = bindings[name][1]
            elif len(args) > scale_index:
                scale = _resolve_scale(args[scale_index], scale_constants)
            else:
                scale = None
            site_tint = tint
            if scale_index is not None and len(args) > TINT_COLOUR_ARG:
                site_tint = _resolve_tint(args[TINT_COLOUR_ARG], tint)
            sites.append(
                BlitSite(
                    sprite=resource.group(1),
                    file=path.name,
                    tint=site_tint,
                    scale=scale,
                    origin=_resolve_origin(
                        args[ORIGIN_X_ARG], args[ORIGIN_Y_ARG], constants
                    ),
                )
            )
    return sites, unattributed


def scan_sources(root: Path) -> tuple[dict[str, list[BlitSite]], list[UnattributedSite]]:
    """Resolve every blit call site under ``root``, grouped by sprite name."""
    by_sprite: dict[str, list[BlitSite]] = {}
    unattributed: list[UnattributedSite] = []
    for path in sorted(root.rglob("*.kt")):
        sites, unresolved = scan_file(path)
        for site in sites:
            by_sprite.setdefault(site.sprite, []).append(site)
        unattributed.extend(unresolved)
    return by_sprite, unattributed
