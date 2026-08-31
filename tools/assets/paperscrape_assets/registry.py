"""The source specification format, and its validation against the shipped set.

`sources/sprites.json` holds one entry per shipped PNG -- including every sprite
that has no source and cannot currently be given one. That is the format's main
job. A registry listing only the sprites that were successfully reconstructed
would read as complete while quietly omitting the sprites that are the actual
problem, so an entry is mandatory and `source.kind` is where the honesty lives:

    "svg"   a committed SVG source renders this sprite
    "none"  no source exists; `source.reason` says why, in terms of what would
            be needed to give it one

Fields
------
``name``            the resource name, matching the PNG stem exactly. This is
                    what `R.drawable.<name>` resolves to on the Kotlin side, so a
                    rename here is an app change and not a tooling change.
``category``        grouping for reports and for future per-category work.
``width``/``height`` declared pixel dimensions. Validated against the shipped
                    PNG: they are part of the sprite's contract with the anchor
                    offsets in `SceneObjectRenderer`, so a source that renders at
                    a different size is a defect, not a variation.
``scale``           `SCENE_UNITS` or `CANVAS_PIXELS`, mirroring Kotlin's
                    `SpriteScale`. Recorded here so the convention has a written
                    home; it is still the caller that selects it at draw time.
                    Moving the selection to this metadata is a later phase.
``tint``            `TINTABLE` (drawn through a runtime `MULTIPLY` colour filter)
                    or `FIXED_ART` (blitted with its own baked colours).
``usage``           `referenced` or `orphan`, derived from the Kotlin sources by
                    `paperscrape-assets validate`.
``contentBox``      `[x0, y0, x1, y1]` in pixels, right and bottom exclusive --
                    the alpha bounding box of what the sprite actually draws.
                    Declared, not merely measured: `width`/`height` have been
                    under contract since schema 1, and the box has not, so until
                    now a regenerated sprite could move its content inside an
                    unchanged canvas and fail nothing.
``anchorRule``      how the anchor is derived from the sprite. See
                    :data:`ANCHOR_RULES`. `UNDETERMINED` is a first-class value,
                    not a placeholder.
``anchor``          `[x, y]` in the sprite's **own local units** -- pixels
                    divided by `SPRITE_PIXELS_PER_UNIT` for a `SCENE_UNITS`
                    sprite, pixels unchanged for `CANVAS_PIXELS` -- measured
                    from the bitmap's own origin. That is the space a call site
                    blits in, so a determined anchor and the origin it is drawn
                    at must sum to zero. Present only when `anchorRule` is not
                    `UNDETERMINED`, and always re-derived by `validate` rather
                    than trusted.
``anchorReason``    why no anchor is determined. Required when, and only when,
                    `anchorRule` is `UNDETERMINED`.
``source``          `{"kind": "svg", "file": "..."}` or
                    `{"kind": "none", "reason": "..."}`.
``notes``           free text; used for the properties a reader would otherwise
                    have to rediscover by measuring.

Why most anchors are `UNDETERMINED`
-----------------------------------
An anchor is a property of the sprite, but the only evidence for it is the
origin a call site blits it at, and that origin is `placement - anchor`: one
equation, two unknowns. It collapses to the anchor alone only when the sprite is
an object in its own right, placed at the object's own position. For a part of a
composite -- a window, a chimney, a scarf -- the origin is a composition
placement and carries no anchor at all, which is why `house_large_window` is
drawn at four different origins.

So an anchor is recorded only where a rule reproduces every observed origin
exactly. Everything else is `UNDETERMINED` with a stated reason, the same way
`source.kind = "none"` records a sprite that cannot be regenerated. Deriving the
remaining anchors means separating placement from anchor at each call site, which
is the re-anchoring work in `ROADMAP.md` Group 4, not a registry change.

Variant groups (schema 3)
-------------------------
A second top-level array, ``variants``, declares sets of sprites that exist
*because they are meant to differ from each other* -- the summer and winter
person art, today; anything else picked per-instance from a lookup table,
tomorrow. Each group carries:

``id``        short identifier, unique within the document.
``axis``      what the members vary along. ``season`` is the only axis so far.
``members``   two or more sprite names, each of which must have its own entry.
``state``     ``DISTINCT`` if the members' pixels differ from one another,
              ``IDENTICAL_GAP`` if they are identical and should not be, or
              ``IDENTICAL_BY_CONSTRUCTION`` if they are identical and always
              will be. The third exists because the second is a *defect* state
              and this library has twenty-four pairs that are neither: the skin
              tones are the man's, the woman's and the boy's own shipped skin
              colours, so each of those characters is identical to one of their
              own variants by definition. Saying ``IDENTICAL_GAP`` about them
              would be filing a bug against arithmetic.
``reason``    why the group is in that state, in terms of what would change it.

The reason this exists is that a variant's *whole purpose* is invisible to every
other check in this file. Sizes, content boxes, anchors, scales and tints are all
per-sprite properties, and two byte-identical files satisfy every one of them
while the feature they implement does nothing. That is what happened: v73 shipped
seasonal outfits for window occupants and drivers, and the summer and winter head
PNGs were the same file, so the feature had no visible effect and nothing failed.

``IDENTICAL_GAP`` is a first-class value, like ``UNDETERMINED`` and
``source.kind = "none"``. It records artwork that is missing, and `validate`
holds the declaration to the bytes in *both* directions: a ``DISTINCT`` group
whose members turn out identical fails, and so does an ``IDENTICAL_GAP`` group
whose members have started to differ. The second direction is the one that
matters in practice -- it is what makes drawing the missing artwork produce a
failure that says so, rather than a silent success nobody records.

Byte-identical sprites that are *not* a variant group are a different thing
entirely: one drawing reached through two names, which Phase 3.4 removed by
deleting the copy and pointing both call sites at the survivor. Those must not
come back, so `validate` also fails on any byte-identical pair that no group
declares.
"""

from __future__ import annotations

import json
from dataclasses import dataclass
from pathlib import Path

# The registry document is at 4. The constant was left at 3 when the schema moved,
# which made every command that loads the registry -- render, validate, compare,
# normalize, all -- fail before doing anything. The loader below already reads and
# validates every field the version 4 document carries, so the constant was simply
# stale rather than the document being ahead of the code.
SCHEMA_VERSION = 4

SCALES = ("SCENE_UNITS", "CANVAS_PIXELS")

#: How far a declared anchor may sit from the one its rule derives, in pixels.
#:
#: A content box of odd width puts `CONTENT_BOTTOM_CENTRE` on a half pixel and the
#: registry stores the rounded value. On a 3x-oversampled sprite that is a third of
#: a local unit, which is below anything visible at any scale the scene uses.
ANCHOR_DERIVATION_TOLERANCE_PX = 1.0

#: How far a blit origin may sit from the one its anchor predicts, in local units.
#:
#: An anchor in pixels converts to thirds of a unit, and a call site writes a
#: rounded literal, so an exact comparison would report a third of a unit as a
#: disagreement. Well below anything visible at any scale the scene uses.
ORIGIN_TOLERANCE = 0.34
TINTS = ("TINTABLE", "FIXED_ART")
SOURCE_KINDS = ("svg", "none")
USAGES = ("referenced", "orphan")

#: What a variant group's members vary along.
#:
#: ``skin`` joins ``season`` because the pixel-based duplicate check made twenty-four skin-tone
#: identities visible that the old byte-based one could not see. They are not seasonal pairs and
#: calling them so would have been the easy lie.
VARIANT_AXES = ("season", "skin")

#: Whether a variant group's members currently differ. ``IDENTICAL_GAP`` is a
#: declaration that artwork is missing, not a tolerance: `validate` fails if such
#: a group's members start to differ, so drawing the missing sprite is reported
#: rather than passing silently.
VARIANT_STATES = ("DISTINCT", "IDENTICAL_GAP", "IDENTICAL_BY_CONSTRUCTION")

#: Mirrors `SpriteBlitter.SPRITE_PIXELS_PER_UNIT` on the Kotlin side.
SPRITE_PIXELS_PER_UNIT = 3

#: How a sprite's anchor is derived. Each is a claim about the artwork that
#: `validate` re-derives, so none of them can drift away from the PNG.
#:
#: ``CONTENT_BOTTOM_CENTRE``
#:     Horizontally centred on the drawn content, vertically on its base. The
#:     ground-anchoring rule: an object standing on the ground meets it along the
#:     bottom edge of what is drawn, not of the canvas around it.
#: ``SPRITE_CENTRE``
#:     The sprite's centre. Only valid where the content is centred in the
#:     bitmap, which `validate` checks -- so trimming padding asymmetrically
#:     fails here instead of silently moving the anchor.
#: ``UNDETERMINED``
#:     No anchor is determined by the evidence. Carries a reason, no anchor.
#: `PART_LOCAL` and `DECLARED_ATTACHMENT` arrived with schema 4 and are documented
#: in `DESIGN_NOTES.md`, but were never added here, so the registry document could
#: not be loaded at all. `UNDETERMINED` is retained: it is a first-class value, and
#: removing it would change what a future gap is allowed to say.
ANCHOR_RULES = (
    "CONTENT_BOTTOM_CENTRE",
    "SPRITE_CENTRE",
    "DECLARED_ATTACHMENT",
    "PART_LOCAL",
    "UNDETERMINED",
)


@dataclass(frozen=True)
class SpriteSpec:
    name: str
    category: str
    width: int
    height: int
    scale: str
    tint: str
    usage: str
    source_kind: str
    source_file: str | None
    source_reason: str | None
    notes: str
    content_box: tuple[int, int, int, int]
    anchor_rule: str
    anchor: tuple[float, float] | None
    anchor_reason: str | None

    @property
    def has_svg_source(self) -> bool:
        return self.source_kind == "svg"

    @property
    def has_anchor(self) -> bool:
        return self.anchor_rule != "UNDETERMINED"

    @property
    def derives_anchor_from_box(self) -> bool:
        """Whether this sprite's anchor is computed from its content box.

        Only the two geometric rules are. `PART_LOCAL` and `DECLARED_ATTACHMENT` are
        *declarations* -- a part's zero, and the joint another sprite attaches to --
        and neither is recoverable from a bounding box, so demanding a derivation
        for them reported forty-nine sprites as failing a rule they do not claim to
        follow. That message even named `SPRITE_CENTRE`'s centring condition, which
        no part had ever asserted.
        """
        return self.anchor_rule in ("CONTENT_BOTTOM_CENTRE", "SPRITE_CENTRE")

    @property
    def predicts_origin(self) -> bool:
        """Whether this sprite's anchor determines where a call site blits it.

        Only for a sprite placed *as a whole* on the point it is anchored to. A
        `PART_LOCAL` sprite is a piece of a larger drawing and sits wherever that
        drawing puts it -- its anchor is its own local zero and predicts nothing
        about the parent's coordinates -- and a `DECLARED_ATTACHMENT` is positioned
        by the joint it attaches to, not by its own box.

        Checking those two against the origin was comparing unrelated numbers. It
        never showed, because the only file that blits them did not resolve at all
        until defect D-4 was fixed.
        """
        return self.anchor_rule in ("CONTENT_BOTTOM_CENTRE", "SPRITE_CENTRE")

    @property
    def units_per_pixel(self) -> float:
        """How many of this sprite's local units one of its pixels spans.

        The two authoring conventions are exactly this factor apart, and it is
        the reason a sprite's size, its convention and its origin are only
        correct together (defect D-1).
        """
        return 1.0 / SPRITE_PIXELS_PER_UNIT if self.scale == "SCENE_UNITS" else 1.0


class RegistryError(Exception):
    """A registry that does not describe the shipped asset set."""


@dataclass(frozen=True)
class VariantGroup:
    """Sprites that exist in order to differ from one another."""

    id: str
    axis: str
    members: tuple[str, ...]
    state: str
    reason: str

    @property
    def must_differ(self) -> bool:
        return self.state == "DISTINCT"


def derive_anchor(
    rule: str,
    content_box: tuple[int, int, int, int],
    size: tuple[int, int],
    units_per_pixel: float = 1.0,
) -> tuple[float, float] | None:
    """The anchor a rule produces, **in the sprite's own pixels**.

    Pixels because that is the unit the registry declares an anchor in -- content
    boxes and anchors are both measured off the PNG -- and the caller compares the
    two directly. Passing ``units_per_pixel`` here converted the derivation to local
    units and then compared it against a pixel declaration, so every `SCENE_UNITS`
    sprite disagreed with itself by a factor of three. It never showed because the
    file carrying most of those sprites did not resolve at all until D-4 was fixed.

    ``units_per_pixel`` is kept as a parameter, defaulting to the identity, for a
    caller that genuinely wants the point in local units.

    Returns ``None`` for ``UNDETERMINED``, and for ``SPRITE_CENTRE`` applied to a
    sprite whose content is not centred in its bitmap -- that combination is not
    a rounding question but a statement that has stopped being true.
    """
    x0, y0, x1, y1 = content_box
    width, height = size
    if rule == "CONTENT_BOTTOM_CENTRE":
        point = ((x0 + x1) / 2.0, float(y1))
    elif rule == "SPRITE_CENTRE":
        # The bitmap's centre, unconditionally. This used to return None unless the
        # content was *also* centred in the bitmap, which conflated two different
        # statements: `SPRITE_CENTRE` says the sprite is placed by its bitmap centre,
        # and says nothing about where its ink sits inside it. A crescent moon's
        # content is off-centre by construction and is still placed by the bitmap
        # centre, so the condition reported four correct declarations as broken.
        point = (width / 2.0, height / 2.0)
    else:
        return None
    return (point[0] * units_per_pixel, point[1] * units_per_pixel)


def _require(condition: bool, message: str) -> None:
    if not condition:
        raise RegistryError(message)


def load(path: Path) -> list[SpriteSpec]:
    document = json.loads(path.read_text(encoding="utf-8"))
    _require(
        document.get("schemaVersion") == SCHEMA_VERSION,
        f"registry schemaVersion must be {SCHEMA_VERSION}, found {document.get('schemaVersion')!r}",
    )
    entries = document.get("sprites")
    _require(isinstance(entries, list), "registry must contain a 'sprites' array")

    specs: list[SpriteSpec] = []
    seen: set[str] = set()
    for raw in entries:
        name = raw.get("name")
        _require(isinstance(name, str) and name, "every sprite entry needs a name")
        _require(name not in seen, f"duplicate registry entry for {name!r}")
        seen.add(name)

        source = raw.get("source") or {}
        kind = source.get("kind")
        _require(kind in SOURCE_KINDS, f"{name}: source.kind must be one of {SOURCE_KINDS}")
        if kind == "svg":
            _require(bool(source.get("file")), f"{name}: an svg source needs a file")
        else:
            _require(bool(source.get("reason")), f"{name}: a sprite with no source needs a reason")

        _require(raw.get("scale") in SCALES, f"{name}: scale must be one of {SCALES}")
        _require(raw.get("tint") in TINTS, f"{name}: tint must be one of {TINTS}")
        _require(raw.get("usage") in USAGES, f"{name}: usage must be one of {USAGES}")
        _require(isinstance(raw.get("width"), int), f"{name}: width must be an integer")
        _require(isinstance(raw.get("height"), int), f"{name}: height must be an integer")

        content_box = raw.get("contentBox")
        _require(
            isinstance(content_box, list)
            and len(content_box) == 4
            and all(isinstance(v, int) for v in content_box),
            f"{name}: contentBox must be a four-element [x0, y0, x1, y1] of integers",
        )
        _require(
            content_box[0] < content_box[2] and content_box[1] < content_box[3],
            f"{name}: contentBox must be non-empty, found {content_box}",
        )
        content_box = (content_box[0], content_box[1], content_box[2], content_box[3])

        anchor_rule = raw.get("anchorRule")
        _require(
            anchor_rule in ANCHOR_RULES,
            f"{name}: anchorRule must be one of {ANCHOR_RULES}",
        )
        anchor = raw.get("anchor")
        anchor_reason = raw.get("anchorReason")
        if anchor_rule == "UNDETERMINED":
            # Mirrors `source.kind = "none"`: the absence is what is being
            # declared, so it carries a reason and must not carry a value that
            # nothing derived.
            _require(
                anchor is None,
                f"{name}: an UNDETERMINED anchorRule must not declare an anchor",
            )
            _require(
                bool(anchor_reason),
                f"{name}: an UNDETERMINED anchorRule needs an anchorReason",
            )
        else:
            _require(
                isinstance(anchor, list) and len(anchor) == 2,
                f"{name}: anchor must be a two-element [x, y]",
            )
            _require(
                anchor_reason is None,
                f"{name}: anchorReason belongs only to an UNDETERMINED anchorRule",
            )
            anchor = (float(anchor[0]), float(anchor[1]))

        specs.append(
            SpriteSpec(
                name=name,
                category=raw.get("category", ""),
                width=raw["width"],
                height=raw["height"],
                scale=raw["scale"],
                tint=raw["tint"],
                usage=raw["usage"],
                source_kind=kind,
                source_file=source.get("file"),
                source_reason=source.get("reason"),
                notes=raw.get("notes", ""),
                content_box=content_box,
                anchor_rule=anchor_rule,
                anchor=anchor,
                anchor_reason=anchor_reason,
            )
        )
    return specs


def load_variants(path: Path, sprite_names: set[str]) -> list[VariantGroup]:
    """The declared variant groups, checked for internal consistency.

    Membership is checked against `sprite_names` here rather than in
    `validate_against_runtime`, because a group naming a sprite that has no entry
    is a malformed document and not a disagreement with the shipped set.
    """
    document = json.loads(path.read_text(encoding="utf-8"))
    entries = document.get("variants")
    _require(isinstance(entries, list), "registry must contain a 'variants' array")

    groups: list[VariantGroup] = []
    seen_ids: set[str] = set()
    claimed: dict[str, str] = {}
    for raw in entries:
        gid = raw.get("id")
        _require(isinstance(gid, str) and gid, "every variant group needs an id")
        _require(gid not in seen_ids, f"duplicate variant group id {gid!r}")
        seen_ids.add(gid)

        _require(raw.get("axis") in VARIANT_AXES, f"{gid}: axis must be one of {VARIANT_AXES}")
        axis = raw["axis"]
        _require(raw.get("state") in VARIANT_STATES, f"{gid}: state must be one of {VARIANT_STATES}")
        # Required in both states, not only for the gap: a group declared DISTINCT
        # is also a claim about the artwork, and the next person needs to know what
        # the distinction is meant to be before deciding a change has broken it.
        _require(bool(raw.get("reason")), f"{gid}: every variant group needs a reason")

        members = raw.get("members")
        _require(
            isinstance(members, list) and len(members) >= 2,
            f"{gid}: members must list at least two sprites",
        )
        _require(len(set(members)) == len(members), f"{gid}: members must not repeat a sprite")
        for member in members:
            _require(member in sprite_names, f"{gid}: member {member!r} has no registry entry")
            # One sprite in two groups **on the same axis** would make the two
            # declarations able to contradict each other about the same bytes.
            # Across axes it is the normal case, and the reason this is keyed by
            # axis rather than by name alone: a summer head belongs to a season
            # pair with its winter self *and* to a skin pair with its own tone
            # variant, and neither declaration says anything about the other.
            _require(
                (axis, member) not in claimed,
                f"{gid}: {member!r} is already a member of "
                f"{claimed.get((axis, member))!r} on the {axis!r} axis",
            )
            claimed[(axis, member)] = gid

        groups.append(
            VariantGroup(
                id=gid,
                axis=raw["axis"],
                members=tuple(members),
                state=raw["state"],
                reason=raw["reason"],
            )
        )
    return groups


def validate_variants(
    groups: list[VariantGroup],
    digests: dict[str, str],
) -> list[str]:
    """Hold every variant declaration to the bytes that actually ship.

    Two independent properties, and the second is the one this phase exists for:

    1. every declared group agrees with its members' bytes, in **both**
       directions -- a `DISTINCT` group whose members are identical has lost the
       distinction it names, an `IDENTICAL_GAP` group whose members now differ
       has gained artwork the declaration has not caught up with, and an
       `IDENTICAL_BY_CONSTRUCTION` group whose members differ has stopped being
       the arithmetic identity it claims to be;
    2. no pixel-identical pair exists that no group declares. A duplicate outside
       a variant group is one drawing reached through two names: two decodes, two
       atlas entries, and two files that can drift apart in one place only.
    """
    problems: list[str] = []

    for group in groups:
        present = [m for m in group.members if m in digests]
        if len(present) < len(group.members):
            # Reported by validate_against_runtime as a missing PNG; not repeated
            # here as a variant failure, which would double-count one mistake.
            continue
        distinct = len({digests[m] for m in present})
        if group.must_differ and distinct == 1:
            problems.append(
                f"variant {group.id}: declared DISTINCT but {', '.join(present)} are "
                f"pixel-identical -- the seasonal difference is not in the artwork"
            )
        elif group.state == "IDENTICAL_GAP" and distinct > 1:
            problems.append(
                f"variant {group.id}: declared IDENTICAL_GAP but {', '.join(present)} now "
                f"differ -- the artwork exists, so move the group to DISTINCT"
            )
        elif group.state == "IDENTICAL_BY_CONSTRUCTION" and distinct > 1:
            problems.append(
                f"variant {group.id}: declared IDENTICAL_BY_CONSTRUCTION but "
                f"{', '.join(present)} now differ -- the identity was arithmetic, so either the "
                f"tone palette or the character's own skin colour moved"
            )

    declared_pairs = {
        frozenset((a, b))
        for group in groups
        for i, a in enumerate(group.members)
        for b in group.members[i + 1 :]
    }
    by_digest: dict[str, list[str]] = {}
    for name, digest in digests.items():
        by_digest.setdefault(digest, []).append(name)
    for names in by_digest.values():
        if len(names) < 2:
            continue
        names = sorted(names)
        for i, a in enumerate(names):
            for b in names[i + 1 :]:
                if frozenset((a, b)) not in declared_pairs:
                    problems.append(
                        f"{a} and {b} are pixel-identical but no variant group declares them: "
                        f"either they are one drawing under two names, and one should go, or "
                        f"they are variants and the registry should say so"
                    )
    return problems


def validate_against_runtime(
    specs: list[SpriteSpec],
    runtime_names: set[str],
    measured_sizes: dict[str, tuple[int, int]],
    measured_content_boxes: dict[str, tuple[int, int, int, int] | None],
    svg_dir: Path,
) -> list[str]:
    """Return every disagreement between the registry and what actually ships.

    Returns problems rather than raising on the first one: a caller fixing the
    registry wants the whole list, not one item per run.
    """
    problems: list[str] = []
    spec_names = {s.name for s in specs}

    for missing in sorted(runtime_names - spec_names):
        problems.append(f"{missing}: ships in res/drawable-nodpi but has no registry entry")
    for extra in sorted(spec_names - runtime_names):
        problems.append(f"{extra}: registry entry with no PNG in res/drawable-nodpi")

    for spec in specs:
        size = measured_sizes.get(spec.name)
        if size and size != (spec.width, spec.height):
            problems.append(
                f"{spec.name}: registry declares {spec.width}x{spec.height}, "
                f"PNG is {size[0]}x{size[1]}"
            )
        measured_box = measured_content_boxes.get(spec.name)
        if measured_box is not None and tuple(measured_box) != spec.content_box:
            problems.append(
                f"{spec.name}: registry declares contentBox {list(spec.content_box)}, "
                f"PNG measures {list(measured_box)}"
            )
        if size:
            derived = derive_anchor(spec.anchor_rule, spec.content_box, size)
            if spec.derives_anchor_from_box and derived is None:
                problems.append(
                    f"{spec.name}: anchorRule {spec.anchor_rule} does not hold for this "
                    "sprite -- its content is not centred in its bitmap"
                )
            elif derived is not None and any(
                abs(a - b) > ANCHOR_DERIVATION_TOLERANCE_PX
                for a, b in zip(spec.anchor, derived)
            ):
                problems.append(
                    f"{spec.name}: registry declares anchor {list(spec.anchor)}, "
                    f"{spec.anchor_rule} derives {[derived[0], derived[1]]}"
                )
        if spec.has_svg_source:
            svg = svg_dir / spec.source_file
            if not svg.is_file():
                problems.append(f"{spec.name}: source file {spec.source_file} is missing")
    return problems


def validate_against_callsites(
    specs: list[SpriteSpec],
    sites_by_sprite: dict[str, list],
) -> tuple[list[str], dict[str, list[str]]]:
    """Compare each declaration against what the Kotlin sources actually do.

    Returns the disagreements, and an *unresolved* map recording every sprite the
    comparison could not reach and why. The two are kept apart deliberately: a
    sprite the resolver cannot see has not agreed with anything, and folding it
    into the pass count would turn "not checked" into "checked and fine" -- which
    is the shape of failure that let defect D-1 ship.
    """
    problems: list[str] = []
    unresolved: dict[str, list[str]] = {}
    for spec in specs:
        sites = sites_by_sprite.get(spec.name, [])
        if not sites:
            unresolved.setdefault(spec.name, []).append(
                "no blit call site names this sprite literally"
            )
            continue
        for site in sites:
            if site.scale is None:
                unresolved.setdefault(spec.name, []).append(
                    f"scale not resolvable at a call site in {site.file}"
                )
            elif site.scale != spec.scale:
                problems.append(
                    f"{spec.name}: registry declares scale {spec.scale}, "
                    f"{site.file} blits it as {site.scale}"
                )
            if site.tint != spec.tint:
                problems.append(
                    f"{spec.name}: registry declares tint {spec.tint}, "
                    f"{site.file} blits it as {site.tint}"
                )
            if site.origin is None:
                unresolved.setdefault(spec.name, []).append(
                    f"origin not resolvable at a call site in {site.file}"
                )
            elif spec.predicts_origin:
                # The anchor is declared in the sprite's own pixels; a call site
                # writes its origin in the units that sprite is blitted in. The two
                # are `units_per_pixel` apart -- the same factor a sprite's size and
                # convention are only correct together across, which is defect D-1 --
                # and this comparison used to skip the conversion entirely.
                expected = (
                    -spec.anchor[0] * spec.units_per_pixel,
                    -spec.anchor[1] * spec.units_per_pixel,
                )
                if any(abs(a - b) > ORIGIN_TOLERANCE for a, b in zip(site.origin, expected)):
                    problems.append(
                        f"{spec.name}: anchor {list(spec.anchor)} implies origin "
                        f"{[round(expected[0], 3), round(expected[1], 3)]}, "
                        f"{site.file} blits it at {list(site.origin)}"
                    )
            elif spec.has_anchor:
                # Recorded as unreached rather than counted as agreeing. A sprite
                # this comparison cannot speak about has not been checked, and
                # folding it into the pass count is the shape of failure that let
                # defect D-1 ship.
                unresolved.setdefault(spec.name, []).append(
                    f"anchorRule {spec.anchor_rule} does not predict a blit origin"
                )
    return problems, unresolved
