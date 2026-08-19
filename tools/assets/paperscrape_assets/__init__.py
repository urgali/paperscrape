"""PaperScrape asset source pipeline.

Offline developer tooling. Nothing in this package is part of the Android build:
Gradle never invokes it, and the app has no dependency on it. It exists so that a
sprite has a source other than the sprite itself.

Layout:

    sources/sprites.json   the registry -- one entry per shipped PNG, including
                           the ones with no recoverable source
    sources/svg/           SVG sources, one file per reconstructed sprite
    staging/               rendered output; never the directory the app reads
    reports/              measurements committed as evidence

See README.md for the pipeline and DESIGN_NOTES.md section 4 for the authoring
conventions the SVG sources must obey.
"""

__all__ = [
    "fidelity",
    "inventory",
    "raster",
    "registry",
    "report",
]
