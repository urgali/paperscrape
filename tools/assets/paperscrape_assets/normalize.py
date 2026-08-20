"""Padding and grid normalisation: the rule, and the plan it produces.

A shipped sprite carries transparent padding that is decoded, held and blitted
for no visual result -- 18.3 MB of it across the set, more than half of everything
the app decodes. Removing it is not a redraw: cropping rows and columns whose
alpha is zero changes no visible pixel. What it does change is where the bitmap's
own pixel (0,0) sits, and `SpriteBlitter` places exactly that pixel at the
caller's origin. So a crop is only correct together with a compensation at the
call site, and this module exists so that the pair is derived by one rule rather
than sprite by sprite.

The rule
--------
The **normalised content box** of a sprite is the union of the measured alpha
bounding boxes of its co-registered group, rounded outward to a multiple of
`SPRITE_PIXELS_PER_UNIT` for a `SCENE_UNITS` sprite and of 1 px for a
`CANVAS_PIXELS` one. The sprite is cropped to that box, and its call site's
origin is compensated by ``trim / unit``.

Two parts of that need their reason recorded, because both look like details and
neither is.

**Why rounded outward, and not to the measured box.** The compensation is
``trim / unit``, and the blitter multiplies the origin by the same ``unit`` again
when it blits. Round to the measured box and a trim of, say, 17 px gives a
compensation of 5.667 units, which comes back as 17.000002 rather than 17 -- a
sub-pixel origin, resampled because the blit paint carries `FILTER_BITMAP_FLAG`.
Rounding outward keeps the compensation an exact integer, at the price of leaving
up to `unit - 1` pixels of padding. That padding is load-bearing for alignment and
is therefore allowed by rule 6.3, which is also why it is *outward*: rounding
inward would crop artwork.

**Why the union over a group.** Some sprites are selected from a lookup table at
draw time -- the 32 walk frames, the window occupants, the car drivers -- so every
member is blitted through one origin literal. Trimming each member to its own box
would need one origin per member, which does not exist and which rule 7.3
forbids; the walk frames would jitter horizontally, since `walk2` reaches 9 px
further left than the others. The union is the box that keeps every member
registered against every other while still removing the padding they all share.

Sprites that share an origin *value* are not a group. `tree_canopy` and
`tree_canopy_snowcap` are both blitted at (-45,-84), but from two separate call
sites with their own literals, so each takes its own trim and its own
compensation. A group is defined by one call site serving many sprites, not by
two call sites agreeing on a number.

What this module does not do
----------------------------
It does not touch anchors. An anchor is `placement - anchor` away from the origin
and 101 of the 118 are `UNDETERMINED`; nothing here resolves one, and nothing here
changes what a placement means. For the 17 determined anchors the invariant
``origin == -anchor`` survives untouched, because the anchor is derived from the
content box and both sides move by the same amount -- `validate` checks that
rather than taking it on trust.
"""

from __future__ import annotations

from dataclasses import dataclass

from .inventory import SPRITE_PIXELS_PER_UNIT, SpriteMeasurement

Box = tuple[int, int, int, int]


@dataclass(frozen=True)
class Group:
    """Sprites that must be cropped by one rectangle because one origin serves them all."""

    key: str
    members: tuple[str, ...]
    origin_site: str
    reason: str


@dataclass(frozen=True)
class Exclusion:
    """A sprite deliberately left as it ships, and why."""

    name: str
    reason: str


#: Sprites reached through a lookup table, so that one origin literal positions
#: all of them. Membership is a pattern rather than a list on purpose: a walk
#: frame added to the table later belongs to the same group by construction, and
#: a list would silently leave it out.
def _matching(names: set[str], predicate) -> tuple[str, ...]:
    return tuple(sorted(n for n in names if predicate(n)))


def co_registered_groups(names: set[str]) -> list[Group]:
    """The lookup-selected groups present in the given sprite set."""
    definitions = (
        (
            "person_walk",
            lambda n: n.startswith("person_") and "_walk" in n,
            "SceneObjectRenderer.drawPerson",
            "Every walk frame of every kind and season is chosen from personWalkDrawables and "
            "blitted through one origin. The frames do not share a content box -- the mid-stride "
            "frame reaches further to both sides -- so a per-frame crop would move the figure "
            "between frames.",
        ),
        (
            "person_head_window",
            lambda n: n.endswith("_head_window"),
            "SceneObjectRenderer.drawWindowOccupant",
            "Occupant heads are chosen from personWindowHeadDrawables and blitted through one "
            "origin, so they must stay registered against the window frame they are drawn into.",
        ),
        (
            "person_head_car",
            lambda n: n.endswith("_head_car"),
            "SceneObjectRenderer.drawCarDriver",
            "Driver heads are chosen from personCarHeadDrawables through a separate origin from "
            "the window occupants, so they form their own group and get their own crop.",
        ),
        (
            "moon_phase",
            lambda n: n.startswith("moon_"),
            "PaperRenderer.drawMoonWithPhase",
            "The phase sprite is chosen at draw time and blitted through one origin. The four "
            "phases are the same disc lit differently, so they must be cropped identically or "
            "the moon would move as it waxes.",
        ),
    )
    groups = []
    for key, predicate, site, reason in definitions:
        members = _matching(names, predicate)
        if members:
            groups.append(Group(key=key, members=members, origin_site=site, reason=reason))
    return groups


#: Sprites this normalisation deliberately leaves alone. Each entry is a decision
#: with a reason, not an oversight: an empty exclusion list would be a claim that
#: every sprite can be normalised, which is not true.
EXCLUSIONS: tuple[Exclusion, ...] = (
    Exclusion(
        "palmtree_fronds",
        "The canvas is 102x176 and 176 is not a multiple of the authoring oversample, so the "
        "sprite is already off the grid. Cropping the empty rows above the fronds is clean, but "
        "the result is still off the grid because its bottom edge is the sprite's own; putting it "
        "on the grid would mean padding back what was removed or cropping artwork. The pair also "
        "shares the hand-tuned -87.45 origin, which is anchor semantics. Deferred to the "
        "perspective work.",
    ),
    Exclusion(
        "palmtree_fronds_frost",
        "Overlays palmtree_fronds at the same origin and shares its off-grid canvas. Deferred for "
        "the same reasons.",
    ),
    *(
        Exclusion(
            name,
            "Anchored on its canvas rather than on its drawing (`SPRITE_CENTRE`), and placed by a "
            "constant this normalisation is not allowed to split. `CELESTIAL_DISC_ORIGIN_UNITS` "
            "positions the sun and all four moon phases from one number, and their content boxes "
            "differ, so cropping each to its own would need that constant separated per sprite and "
            "the anchor rule changed with it -- an anchor-model decision, not padding removal. "
            "`SkySpriteAnchoringTest` pins the surviving relationship between each canvas and its "
            "origin, and it is the test that caught defect D-1 twice.",
        )
        for name in (
            "sun_body", "moon_full", "moon_half", "moon_gibbous", "moon_crescent", "moon_jack_o_lantern",
        )
    ),
    Exclusion(
        "sun_glow",
        "`SPRITE_CENTRE`, and its padding is one pixel on each side of a 396x396 canvas. Rounded "
        "outward to the sprite grid that removes nothing at all, so there is no crop to make.",
    ),
    Exclusion(
        "star_sparkle",
        "`SPRITE_CENTRE`, positioned by `STAR_SPRITE_ORIGIN_UNITS` against a nominal 32-unit star "
        "radius. The crop is symmetric and would be safe on its own, but it moves the canvas the "
        "origin constant is expressed against, and that constant is the one D-1 broke. Left with "
        "the other canvas-anchored sprites so the sky set moves as one decision or not at all.",
    ),
    Exclusion(
        "firework",
        "`SPRITE_CENTRE`, blitted centred on the burst it draws. Same reasoning as `star_sparkle`: "
        "a symmetric crop is expressible, but it redefines the canvas the origin is measured "
        "against, which is a change to the sky sprites' anchoring rather than to their padding.",
    ),
)


def excluded_names() -> set[str]:
    return {e.name for e in EXCLUSIONS}


def unit_for(scale: str) -> int:
    """The grid one of this sprite's local units is worth, in pixels."""
    return SPRITE_PIXELS_PER_UNIT if scale == "SCENE_UNITS" else 1


def normalised_box(boxes: list[Box], size: tuple[int, int], unit: int) -> Box:
    """The union of `boxes`, rounded outward to the sprite grid and clamped to the canvas.

    The rounding grid is `SPRITE_PIXELS_PER_UNIT` for **every** sprite, not only the
    `SCENE_UNITS` ones. `unit` still governs the compensation -- a `CANVAS_PIXELS`
    sprite's origin is written in pixels, so its trim converts one for one -- but the
    canvas itself has to stay a whole multiple of the grid on both axes whatever
    convention positions it, because `SpriteGeometryTest` requires that of the whole
    shipped set and makes no exception. Rounding a `CANVAS_PIXELS` sprite to its own
    pixel instead took `bird_body` to 88x21, which is off the grid on both axes.
    Rounding outward can only leave padding behind, never remove artwork, and a trim
    that is a multiple of the grid is still a whole number of pixels.
    """
    if not boxes:
        raise ValueError("a normalised box needs at least one content box")
    width, height = size
    grid = SPRITE_PIXELS_PER_UNIT
    left = min(b[0] for b in boxes) // grid * grid
    top = min(b[1] for b in boxes) // grid * grid
    right = -(-max(b[2] for b in boxes) // grid) * grid
    bottom = -(-max(b[3] for b in boxes) // grid) * grid
    return (max(0, left), max(0, top), min(width, right), min(height, bottom))


@dataclass(frozen=True)
class Normalisation:
    """One crop, and the origin compensation that has to go with it."""

    key: str
    members: tuple[str, ...]
    unit: int
    size: tuple[int, int]
    box: Box
    origin_site: str | None

    @property
    def new_size(self) -> tuple[int, int]:
        return (self.box[2] - self.box[0], self.box[3] - self.box[1])

    @property
    def compensation(self) -> tuple[float, float]:
        """Units to add to the call site's origin, exact by construction."""
        return (self.box[0] / self.unit, self.box[1] / self.unit)

    @property
    def is_noop(self) -> bool:
        return self.new_size == self.size

    @property
    def recovered_bytes(self) -> int:
        before = self.size[0] * self.size[1]
        after = self.new_size[0] * self.new_size[1]
        return (before - after) * 4 * len(self.members)


def plan(
    measurements: dict[str, SpriteMeasurement],
    scales: dict[str, str],
    referenced: set[str],
) -> list[Normalisation]:
    """Every normalisation the rule produces for the given sprite set.

    Sprites with no reference from the Kotlin sources are skipped: there is no
    call site to compensate, so cropping one would be a change with nobody to
    keep it correct. Whether those sprites should exist at all is a separate
    question, and answering it by quietly cropping them would be the wrong way
    round.
    """
    excluded = excluded_names()
    names = set(measurements) - excluded
    grouped: set[str] = set()
    results: list[Normalisation] = []

    for group in co_registered_groups(names):
        members = tuple(n for n in group.members if n in referenced)
        grouped.update(group.members)
        if not members:
            continue
        sizes = {(measurements[n].width, measurements[n].height) for n in members}
        if len(sizes) != 1:
            raise ValueError(
                f"{group.key}: members must share a canvas, found {sorted(sizes)}"
            )
        units = {unit_for(scales[n]) for n in members}
        if len(units) != 1:
            raise ValueError(
                f"{group.key}: members must share a scale convention, found {sorted(units)}"
            )
        size = sizes.pop()
        unit = units.pop()
        boxes = [measurements[n].content_bbox for n in members if measurements[n].content_bbox]
        results.append(
            Normalisation(
                key=group.key,
                members=members,
                unit=unit,
                size=size,
                box=normalised_box(boxes, size, unit),
                origin_site=group.origin_site,
            )
        )

    for name in sorted(names - grouped):
        if name not in referenced:
            continue
        measurement = measurements[name]
        box = measurement.content_bbox
        if box is None:
            continue
        unit = unit_for(scales[name])
        size = (measurement.width, measurement.height)
        results.append(
            Normalisation(
                key=name,
                members=(name,),
                unit=unit,
                size=size,
                box=normalised_box([box], size, unit),
                origin_site=None,
            )
        )
    return results


def pending(plans: list[Normalisation]) -> list[Normalisation]:
    """The normalisations that still have something to remove."""
    return [p for p in plans if not p.is_noop]


#: Anchor rules whose anchor is the *canvas* rather than the drawing inside it.
#: A trailing crop changes the canvas, so it moves their anchor and is not free.
CANVAS_ANCHORED_RULES = ("SPRITE_CENTRE",)


def trailing(item: Normalisation, grid: int = SPRITE_PIXELS_PER_UNIT) -> Normalisation:
    """The part of ``item``'s crop that costs nothing at the call site.

    The full rule removes padding on all four sides and pays for it with an origin
    compensation, because `SpriteBlitter` puts the bitmap's pixel (0,0) on the
    caller's origin and cropping the left or the top moves what that pixel is. The
    trailing crop removes padding on the **right and bottom only**. Pixel (0,0) is
    untouched, every drawn pixel keeps the coordinates it had, and no call site,
    anchor or content box moves. Nothing outside `GlTextureAtlas` and
    `CanvasSceneTarget` reads a sprite's dimensions at all, and both derive
    everything they need from the bitmap they are handed.

    That makes this the half of the padding that can be removed without the
    re-anchoring decision the rest of it needs, and without a device to confirm
    afterwards that nothing moved -- because nothing can have.

    Two conditions still apply.

    **Rounded outward to ``grid``, for every sprite and not only the `SCENE_UNITS`
    ones.** `SpriteGeometryTest` requires every shipped canvas to be a whole
    multiple of `SpriteBlitter.SPRITE_PIXELS_PER_UNIT` on both axes, and it makes
    no exception for the raw-pixel convention. Rounding outward can only leave
    padding behind, never remove artwork.

    **Not for a canvas-anchored sprite.** `SPRITE_CENTRE` says the sprite is placed
    by the centre of its bitmap, so changing the bitmap's width or height moves the
    anchor even though no drawn pixel moved. Those sprites are left to the full
    rule -- see `CANVAS_ANCHORED_RULES`.
    """
    right = -(-item.box[2] // grid) * grid
    bottom = -(-item.box[3] // grid) * grid
    box = (0, 0, min(item.size[0], right), min(item.size[1], bottom))
    return Normalisation(
        key=item.key,
        members=item.members,
        unit=item.unit,
        size=item.size,
        box=box,
        origin_site=item.origin_site,
    )


def trailing_plan(
    plans: list[Normalisation],
    anchor_rules: dict[str, str],
    grid: int = SPRITE_PIXELS_PER_UNIT,
) -> list[Normalisation]:
    """Every trailing crop worth performing, in the order `plan` produced them."""
    results = []
    for item in plans:
        if any(anchor_rules[n] in CANVAS_ANCHORED_RULES for n in item.members):
            continue
        candidate = trailing(item, grid)
        if not candidate.is_noop:
            results.append(candidate)
    return results
