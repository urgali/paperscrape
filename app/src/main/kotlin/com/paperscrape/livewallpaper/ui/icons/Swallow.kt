package com.paperscrape.livewallpaper.ui.icons

import androidx.compose.material.icons.materialIcon
import androidx.compose.material.icons.materialPath
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * *Birds*' icon in the settings: the scene's own swallow (`tools/assets/sources/svg/bird_body.svg`, 51 x 21), the one
 * icon of the settings that is not Material's (`DESIGN_NOTES.md` §9, "Icons", decision 107). Its four papers -- the far
 * wing, the tail, the body, the near wing -- in one colour, as an icon is drawn, turned 20 degrees up as in flight
 * ([SWALLOW_TURN_DEGREES]), [SWALLOW_SPAN] wide from x = 1 and centred on y = 12 in the 24 x 24 of a Material icon, so it
 * stands as wide as its neighbours. The points are the SVG's, turned and fitted, to three decimals (`SwallowIconTest`
 * works them out again from the SVG): a redraw of the bird is a redraw of the icon.
 */
internal val Swallow: ImageVector
    get() {
        _swallow?.let { return it }
        return materialIcon(name = "PaperScrape.Swallow") {
            materialPath {
                moveTo(15.952f, 8.073f)
                lineTo(13.663f, 7.816f)
                lineTo(10.418f, 7.547f)
                lineTo(7.413f, 7.344f)
                lineTo(5.231f, 7.609f)
                lineTo(7.028f, 7.829f)
                lineTo(9.515f, 8.544f)
                lineTo(11.662f, 9.269f)
                lineTo(12.541f, 9.777f)
                close()
            }
            materialPath {
                moveTo(6.721f, 12.425f)
                lineTo(1.000f, 13.067f)
                lineTo(2.025f, 13.553f)
                lineTo(5.843f, 13.573f)
                lineTo(2.861f, 16.062f)
                lineTo(2.434f, 16.937f)
                lineTo(7.297f, 14.134f)
                close()
            }
            materialPath {
                moveTo(5.627f, 12.710f)
                lineTo(8.141f, 11.224f)
                lineTo(12.169f, 9.321f)
                lineTo(15.984f, 8.303f)
                lineTo(18.236f, 7.678f)
                lineTo(19.730f, 7.063f)
                lineTo(21.498f, 7.175f)
                lineTo(23.000f, 7.189f)
                lineTo(21.696f, 8.157f)
                lineTo(20.339f, 9.345f)
                lineTo(18.206f, 10.718f)
                lineTo(14.793f, 11.925f)
                lineTo(10.258f, 13.452f)
                lineTo(6.767f, 14.218f)
                close()
            }
            materialPath {
                moveTo(15.002f, 8.727f)
                lineTo(12.491f, 7.959f)
                lineTo(9.335f, 7.329f)
                lineTo(5.520f, 7.133f)
                lineTo(3.013f, 7.449f)
                lineTo(4.734f, 8.011f)
                lineTo(7.408f, 8.673f)
                lineTo(9.978f, 9.476f)
                lineTo(11.034f, 10.243f)
                close()
            }
        }.also { _swallow = it }
    }

private var _swallow: ImageVector? = null

/** How far the swallow is turned up, nose first, from the scene's level bird. */
internal const val SWALLOW_TURN_DEGREES = 20f

/** How wide the turned swallow stands in the icon's 24 units. */
internal const val SWALLOW_SPAN = 22f
