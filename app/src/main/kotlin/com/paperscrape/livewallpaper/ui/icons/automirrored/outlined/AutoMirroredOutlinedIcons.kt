/*
 * Copyright 2020 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.paperscrape.livewallpaper.ui.icons.automirrored.outlined

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.materialIcon
import androidx.compose.material.icons.materialPath
import androidx.compose.ui.graphics.vector.ImageVector

/*
 * The `Icons.AutoMirrored.Outlined` icons the settings draw that `material-icons-core` does not hold (DirectionsWalk),
 * from `androidx.compose.material:material-icons-extended` 1.7.8 (The Android Open Source Project, Apache License
 * 2.0): each path is that library's, call for call and number for number, so each icon draws the same pixels as the
 * library's.
 */

internal val Icons.AutoMirrored.Outlined.DirectionsWalk: ImageVector
    get() {
        _directionsWalk?.let { return it }
        return materialIcon(name = "AutoMirrored.Outlined.DirectionsWalk", autoMirror = true) {
            materialPath {
                moveTo(13.5f, 5.5f)
                curveToRelative(1.1f, 0.0f, 2.0f, -0.9f, 2.0f, -2.0f)
                reflectiveCurveToRelative(-0.9f, -2.0f, -2.0f, -2.0f)
                reflectiveCurveToRelative(-2.0f, 0.9f, -2.0f, 2.0f)
                reflectiveCurveToRelative(0.9f, 2.0f, 2.0f, 2.0f)
                close()
                moveTo(9.8f, 8.9f)
                lineTo(7.0f, 23.0f)
                horizontalLineToRelative(2.1f)
                lineToRelative(1.8f, -8.0f)
                lineToRelative(2.1f, 2.0f)
                verticalLineToRelative(6.0f)
                horizontalLineToRelative(2.0f)
                verticalLineToRelative(-7.5f)
                lineToRelative(-2.1f, -2.0f)
                lineToRelative(0.6f, -3.0f)
                curveTo(14.8f, 12.0f, 16.8f, 13.0f, 19.0f, 13.0f)
                verticalLineToRelative(-2.0f)
                curveToRelative(-1.9f, 0.0f, -3.5f, -1.0f, -4.3f, -2.4f)
                lineToRelative(-1.0f, -1.6f)
                curveToRelative(-0.56f, -0.89f, -1.68f, -1.25f, -2.65f, -0.84f)
                lineTo(6.0f, 8.3f)
                verticalLineTo(13.0f)
                horizontalLineToRelative(2.0f)
                verticalLineTo(9.6f)
                lineToRelative(1.8f, -0.7f)
            }
        }.also { _directionsWalk = it }
    }

private var _directionsWalk: ImageVector? = null
