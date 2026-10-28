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

package com.paperscrape.livewallpaper.ui.icons.outlined

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.materialIcon
import androidx.compose.material.icons.materialPath
import androidx.compose.ui.graphics.vector.ImageVector

/*
 * The `Icons.Outlined` icons the settings draw that `material-icons-core` does not hold (AccountBalance, AcUnit,
 * Apartment, CardGiftcard, Celebration, Cloud, Cottage, DarkMode, DirectionsCar, Egg, FileOpen, Filter, FilterDrama,
 * Flare, House, Landscape, LocalFlorist, Park, Restore, Save, Schedule, StarBorder, Storefront, SystemUpdate, Terrain,
 * WaterDrop, Waves, WbSunny), from `androidx.compose.material:material-icons-extended` 1.7.8 (The Android Open Source
 * Project, Apache License 2.0): each path is that library's, call for call and number for number, so each icon draws
 * the same pixels as the library's.
 */

internal val Icons.Outlined.AccountBalance: ImageVector
    get() {
        _accountBalance?.let { return it }
        return materialIcon(name = "Outlined.AccountBalance") {
            materialPath {
                moveTo(6.5f, 10.0f)
                horizontalLineToRelative(-2.0f)
                verticalLineToRelative(7.0f)
                horizontalLineToRelative(2.0f)
                verticalLineToRelative(-7.0f)
                close()
                moveTo(12.5f, 10.0f)
                horizontalLineToRelative(-2.0f)
                verticalLineToRelative(7.0f)
                horizontalLineToRelative(2.0f)
                verticalLineToRelative(-7.0f)
                close()
                moveTo(21.0f, 19.0f)
                lineTo(2.0f, 19.0f)
                verticalLineToRelative(2.0f)
                horizontalLineToRelative(19.0f)
                verticalLineToRelative(-2.0f)
                close()
                moveTo(18.5f, 10.0f)
                horizontalLineToRelative(-2.0f)
                verticalLineToRelative(7.0f)
                horizontalLineToRelative(2.0f)
                verticalLineToRelative(-7.0f)
                close()
                moveTo(11.5f, 3.26f)
                lineTo(16.71f, 6.0f)
                lineTo(6.29f, 6.0f)
                lineToRelative(5.21f, -2.74f)
                moveToRelative(0.0f, -2.26f)
                lineTo(2.0f, 6.0f)
                verticalLineToRelative(2.0f)
                horizontalLineToRelative(19.0f)
                lineTo(21.0f, 6.0f)
                lineToRelative(-9.5f, -5.0f)
                close()
            }
        }.also { _accountBalance = it }
    }

private var _accountBalance: ImageVector? = null

internal val Icons.Outlined.AcUnit: ImageVector
    get() {
        _acUnit?.let { return it }
        return materialIcon(name = "Outlined.AcUnit") {
            materialPath {
                moveTo(22.0f, 11.0f)
                horizontalLineToRelative(-4.17f)
                lineToRelative(3.24f, -3.24f)
                lineToRelative(-1.41f, -1.42f)
                lineTo(15.0f, 11.0f)
                horizontalLineToRelative(-2.0f)
                verticalLineTo(9.0f)
                lineToRelative(4.66f, -4.66f)
                lineToRelative(-1.42f, -1.41f)
                lineTo(13.0f, 6.17f)
                verticalLineTo(2.0f)
                horizontalLineToRelative(-2.0f)
                verticalLineToRelative(4.17f)
                lineTo(7.76f, 2.93f)
                lineTo(6.34f, 4.34f)
                lineTo(11.0f, 9.0f)
                verticalLineToRelative(2.0f)
                horizontalLineTo(9.0f)
                lineTo(4.34f, 6.34f)
                lineTo(2.93f, 7.76f)
                lineTo(6.17f, 11.0f)
                horizontalLineTo(2.0f)
                verticalLineToRelative(2.0f)
                horizontalLineToRelative(4.17f)
                lineToRelative(-3.24f, 3.24f)
                lineToRelative(1.41f, 1.42f)
                lineTo(9.0f, 13.0f)
                horizontalLineToRelative(2.0f)
                verticalLineToRelative(2.0f)
                lineToRelative(-4.66f, 4.66f)
                lineToRelative(1.42f, 1.41f)
                lineTo(11.0f, 17.83f)
                verticalLineTo(22.0f)
                horizontalLineToRelative(2.0f)
                verticalLineToRelative(-4.17f)
                lineToRelative(3.24f, 3.24f)
                lineToRelative(1.42f, -1.41f)
                lineTo(13.0f, 15.0f)
                verticalLineToRelative(-2.0f)
                horizontalLineToRelative(2.0f)
                lineToRelative(4.66f, 4.66f)
                lineToRelative(1.41f, -1.42f)
                lineTo(17.83f, 13.0f)
                horizontalLineTo(22.0f)
                verticalLineToRelative(-2.0f)
                close()
            }
        }.also { _acUnit = it }
    }

private var _acUnit: ImageVector? = null

internal val Icons.Outlined.Apartment: ImageVector
    get() {
        _apartment?.let { return it }
        return materialIcon(name = "Outlined.Apartment") {
            materialPath {
                moveTo(17.0f, 11.0f)
                verticalLineTo(3.0f)
                horizontalLineTo(7.0f)
                verticalLineToRelative(4.0f)
                horizontalLineTo(3.0f)
                verticalLineToRelative(14.0f)
                horizontalLineToRelative(8.0f)
                verticalLineToRelative(-4.0f)
                horizontalLineToRelative(2.0f)
                verticalLineToRelative(4.0f)
                horizontalLineToRelative(8.0f)
                verticalLineTo(11.0f)
                horizontalLineTo(17.0f)
                close()
                moveTo(7.0f, 19.0f)
                horizontalLineTo(5.0f)
                verticalLineToRelative(-2.0f)
                horizontalLineToRelative(2.0f)
                verticalLineTo(19.0f)
                close()
                moveTo(7.0f, 15.0f)
                horizontalLineTo(5.0f)
                verticalLineToRelative(-2.0f)
                horizontalLineToRelative(2.0f)
                verticalLineTo(15.0f)
                close()
                moveTo(7.0f, 11.0f)
                horizontalLineTo(5.0f)
                verticalLineTo(9.0f)
                horizontalLineToRelative(2.0f)
                verticalLineTo(11.0f)
                close()
                moveTo(11.0f, 15.0f)
                horizontalLineTo(9.0f)
                verticalLineToRelative(-2.0f)
                horizontalLineToRelative(2.0f)
                verticalLineTo(15.0f)
                close()
                moveTo(11.0f, 11.0f)
                horizontalLineTo(9.0f)
                verticalLineTo(9.0f)
                horizontalLineToRelative(2.0f)
                verticalLineTo(11.0f)
                close()
                moveTo(11.0f, 7.0f)
                horizontalLineTo(9.0f)
                verticalLineTo(5.0f)
                horizontalLineToRelative(2.0f)
                verticalLineTo(7.0f)
                close()
                moveTo(15.0f, 15.0f)
                horizontalLineToRelative(-2.0f)
                verticalLineToRelative(-2.0f)
                horizontalLineToRelative(2.0f)
                verticalLineTo(15.0f)
                close()
                moveTo(15.0f, 11.0f)
                horizontalLineToRelative(-2.0f)
                verticalLineTo(9.0f)
                horizontalLineToRelative(2.0f)
                verticalLineTo(11.0f)
                close()
                moveTo(15.0f, 7.0f)
                horizontalLineToRelative(-2.0f)
                verticalLineTo(5.0f)
                horizontalLineToRelative(2.0f)
                verticalLineTo(7.0f)
                close()
                moveTo(19.0f, 19.0f)
                horizontalLineToRelative(-2.0f)
                verticalLineToRelative(-2.0f)
                horizontalLineToRelative(2.0f)
                verticalLineTo(19.0f)
                close()
                moveTo(19.0f, 15.0f)
                horizontalLineToRelative(-2.0f)
                verticalLineToRelative(-2.0f)
                horizontalLineToRelative(2.0f)
                verticalLineTo(15.0f)
                close()
            }
        }.also { _apartment = it }
    }

private var _apartment: ImageVector? = null

internal val Icons.Outlined.CardGiftcard: ImageVector
    get() {
        _cardGiftcard?.let { return it }
        return materialIcon(name = "Outlined.CardGiftcard") {
            materialPath {
                moveTo(20.0f, 6.0f)
                horizontalLineToRelative(-2.18f)
                curveToRelative(0.11f, -0.31f, 0.18f, -0.65f, 0.18f, -1.0f)
                curveToRelative(0.0f, -1.66f, -1.34f, -3.0f, -3.0f, -3.0f)
                curveToRelative(-1.05f, 0.0f, -1.96f, 0.54f, -2.5f, 1.35f)
                lineToRelative(-0.5f, 0.67f)
                lineToRelative(-0.5f, -0.68f)
                curveTo(10.96f, 2.54f, 10.05f, 2.0f, 9.0f, 2.0f)
                curveTo(7.34f, 2.0f, 6.0f, 3.34f, 6.0f, 5.0f)
                curveToRelative(0.0f, 0.35f, 0.07f, 0.69f, 0.18f, 1.0f)
                lineTo(4.0f, 6.0f)
                curveToRelative(-1.11f, 0.0f, -1.99f, 0.89f, -1.99f, 2.0f)
                lineTo(2.0f, 19.0f)
                curveToRelative(0.0f, 1.11f, 0.89f, 2.0f, 2.0f, 2.0f)
                horizontalLineToRelative(16.0f)
                curveToRelative(1.11f, 0.0f, 2.0f, -0.89f, 2.0f, -2.0f)
                lineTo(22.0f, 8.0f)
                curveToRelative(0.0f, -1.11f, -0.89f, -2.0f, -2.0f, -2.0f)
                close()
                moveTo(15.0f, 4.0f)
                curveToRelative(0.55f, 0.0f, 1.0f, 0.45f, 1.0f, 1.0f)
                reflectiveCurveToRelative(-0.45f, 1.0f, -1.0f, 1.0f)
                reflectiveCurveToRelative(-1.0f, -0.45f, -1.0f, -1.0f)
                reflectiveCurveToRelative(0.45f, -1.0f, 1.0f, -1.0f)
                close()
                moveTo(9.0f, 4.0f)
                curveToRelative(0.55f, 0.0f, 1.0f, 0.45f, 1.0f, 1.0f)
                reflectiveCurveToRelative(-0.45f, 1.0f, -1.0f, 1.0f)
                reflectiveCurveToRelative(-1.0f, -0.45f, -1.0f, -1.0f)
                reflectiveCurveToRelative(0.45f, -1.0f, 1.0f, -1.0f)
                close()
                moveTo(20.0f, 19.0f)
                lineTo(4.0f, 19.0f)
                verticalLineToRelative(-2.0f)
                horizontalLineToRelative(16.0f)
                verticalLineToRelative(2.0f)
                close()
                moveTo(20.0f, 14.0f)
                lineTo(4.0f, 14.0f)
                lineTo(4.0f, 8.0f)
                horizontalLineToRelative(5.08f)
                lineTo(7.0f, 10.83f)
                lineTo(8.62f, 12.0f)
                lineTo(12.0f, 7.4f)
                lineToRelative(3.38f, 4.6f)
                lineTo(17.0f, 10.83f)
                lineTo(14.92f, 8.0f)
                lineTo(20.0f, 8.0f)
                verticalLineToRelative(6.0f)
                close()
            }
        }.also { _cardGiftcard = it }
    }

private var _cardGiftcard: ImageVector? = null

internal val Icons.Outlined.Celebration: ImageVector
    get() {
        _celebration?.let { return it }
        return materialIcon(name = "Outlined.Celebration") {
            materialPath {
                moveTo(2.0f, 22.0f)
                lineToRelative(14.0f, -5.0f)
                lineTo(7.0f, 8.0f)
                lineTo(2.0f, 22.0f)
                close()
                moveTo(12.35f, 16.18f)
                lineTo(5.3f, 18.7f)
                lineToRelative(2.52f, -7.05f)
                lineTo(12.35f, 16.18f)
                close()
            }
            materialPath {
                moveTo(14.53f, 12.53f)
                lineToRelative(5.59f, -5.59f)
                curveToRelative(0.49f, -0.49f, 1.28f, -0.49f, 1.77f, 0.0f)
                lineToRelative(0.59f, 0.59f)
                lineToRelative(1.06f, -1.06f)
                lineToRelative(-0.59f, -0.59f)
                curveToRelative(-1.07f, -1.07f, -2.82f, -1.07f, -3.89f, 0.0f)
                lineToRelative(-5.59f, 5.59f)
                lineTo(14.53f, 12.53f)
                close()
            }
            materialPath {
                moveTo(10.06f, 6.88f)
                lineTo(9.47f, 7.47f)
                lineToRelative(1.06f, 1.06f)
                lineToRelative(0.59f, -0.59f)
                curveToRelative(1.07f, -1.07f, 1.07f, -2.82f, 0.0f, -3.89f)
                lineToRelative(-0.59f, -0.59f)
                lineTo(9.47f, 4.53f)
                lineToRelative(0.59f, 0.59f)
                curveTo(10.54f, 5.6f, 10.54f, 6.4f, 10.06f, 6.88f)
                close()
            }
            materialPath {
                moveTo(17.06f, 11.88f)
                lineToRelative(-1.59f, 1.59f)
                lineToRelative(1.06f, 1.06f)
                lineToRelative(1.59f, -1.59f)
                curveToRelative(0.49f, -0.49f, 1.28f, -0.49f, 1.77f, 0.0f)
                lineToRelative(1.61f, 1.61f)
                lineToRelative(1.06f, -1.06f)
                lineToRelative(-1.61f, -1.61f)
                curveTo(19.87f, 10.81f, 18.13f, 10.81f, 17.06f, 11.88f)
                close()
            }
            materialPath {
                moveTo(15.06f, 5.88f)
                lineToRelative(-3.59f, 3.59f)
                lineToRelative(1.06f, 1.06f)
                lineToRelative(3.59f, -3.59f)
                curveToRelative(1.07f, -1.07f, 1.07f, -2.82f, 0.0f, -3.89f)
                lineToRelative(-1.59f, -1.59f)
                lineToRelative(-1.06f, 1.06f)
                lineToRelative(1.59f, 1.59f)
                curveTo(15.54f, 4.6f, 15.54f, 5.4f, 15.06f, 5.88f)
                close()
            }
        }.also { _celebration = it }
    }

private var _celebration: ImageVector? = null

internal val Icons.Outlined.Cloud: ImageVector
    get() {
        _cloud?.let { return it }
        return materialIcon(name = "Outlined.Cloud") {
            materialPath {
                moveTo(12.0f, 6.0f)
                curveToRelative(2.62f, 0.0f, 4.88f, 1.86f, 5.39f, 4.43f)
                lineToRelative(0.3f, 1.5f)
                lineToRelative(1.53f, 0.11f)
                curveToRelative(1.56f, 0.1f, 2.78f, 1.41f, 2.78f, 2.96f)
                curveToRelative(0.0f, 1.65f, -1.35f, 3.0f, -3.0f, 3.0f)
                horizontalLineTo(6.0f)
                curveToRelative(-2.21f, 0.0f, -4.0f, -1.79f, -4.0f, -4.0f)
                curveToRelative(0.0f, -2.05f, 1.53f, -3.76f, 3.56f, -3.97f)
                lineToRelative(1.07f, -0.11f)
                lineToRelative(0.5f, -0.95f)
                curveTo(8.08f, 7.14f, 9.94f, 6.0f, 12.0f, 6.0f)
                moveToRelative(0.0f, -2.0f)
                curveTo(9.11f, 4.0f, 6.6f, 5.64f, 5.35f, 8.04f)
                curveTo(2.34f, 8.36f, 0.0f, 10.91f, 0.0f, 14.0f)
                curveToRelative(0.0f, 3.31f, 2.69f, 6.0f, 6.0f, 6.0f)
                horizontalLineToRelative(13.0f)
                curveToRelative(2.76f, 0.0f, 5.0f, -2.24f, 5.0f, -5.0f)
                curveToRelative(0.0f, -2.64f, -2.05f, -4.78f, -4.65f, -4.96f)
                curveTo(18.67f, 6.59f, 15.64f, 4.0f, 12.0f, 4.0f)
                close()
            }
        }.also { _cloud = it }
    }

private var _cloud: ImageVector? = null

internal val Icons.Outlined.Cottage: ImageVector
    get() {
        _cottage?.let { return it }
        return materialIcon(name = "Outlined.Cottage") {
            materialPath {
                moveTo(12.0f, 3.0f)
                lineTo(6.0f, 7.58f)
                verticalLineTo(6.0f)
                horizontalLineTo(4.0f)
                verticalLineToRelative(3.11f)
                lineTo(1.0f, 11.4f)
                lineToRelative(1.21f, 1.59f)
                lineTo(4.0f, 11.62f)
                verticalLineTo(21.0f)
                horizontalLineToRelative(16.0f)
                verticalLineToRelative(-9.38f)
                lineToRelative(1.79f, 1.36f)
                lineTo(23.0f, 11.4f)
                lineTo(12.0f, 3.0f)
                close()
                moveTo(18.0f, 19.0f)
                horizontalLineToRelative(-5.0f)
                verticalLineToRelative(-4.0f)
                horizontalLineToRelative(-2.0f)
                verticalLineToRelative(4.0f)
                horizontalLineTo(6.0f)
                verticalLineToRelative(-8.9f)
                lineToRelative(6.0f, -4.58f)
                lineToRelative(6.0f, 4.58f)
                verticalLineTo(19.0f)
                close()
                moveTo(10.0f, 1.0f)
                curveToRelative(0.0f, 1.66f, -1.34f, 3.0f, -3.0f, 3.0f)
                curveTo(6.45f, 4.0f, 6.0f, 4.45f, 6.0f, 5.0f)
                horizontalLineTo(4.0f)
                curveToRelative(0.0f, -1.66f, 1.34f, -3.0f, 3.0f, -3.0f)
                curveToRelative(0.55f, 0.0f, 1.0f, -0.45f, 1.0f, -1.0f)
                horizontalLineTo(10.0f)
                close()
            }
        }.also { _cottage = it }
    }

private var _cottage: ImageVector? = null

internal val Icons.Outlined.DarkMode: ImageVector
    get() {
        _darkMode?.let { return it }
        return materialIcon(name = "Outlined.DarkMode") {
            materialPath {
                moveTo(9.37f, 5.51f)
                curveTo(9.19f, 6.15f, 9.1f, 6.82f, 9.1f, 7.5f)
                curveToRelative(0.0f, 4.08f, 3.32f, 7.4f, 7.4f, 7.4f)
                curveToRelative(0.68f, 0.0f, 1.35f, -0.09f, 1.99f, -0.27f)
                curveTo(17.45f, 17.19f, 14.93f, 19.0f, 12.0f, 19.0f)
                curveToRelative(-3.86f, 0.0f, -7.0f, -3.14f, -7.0f, -7.0f)
                curveTo(5.0f, 9.07f, 6.81f, 6.55f, 9.37f, 5.51f)
                close()
                moveTo(12.0f, 3.0f)
                curveToRelative(-4.97f, 0.0f, -9.0f, 4.03f, -9.0f, 9.0f)
                reflectiveCurveToRelative(4.03f, 9.0f, 9.0f, 9.0f)
                reflectiveCurveToRelative(9.0f, -4.03f, 9.0f, -9.0f)
                curveToRelative(0.0f, -0.46f, -0.04f, -0.92f, -0.1f, -1.36f)
                curveToRelative(-0.98f, 1.37f, -2.58f, 2.26f, -4.4f, 2.26f)
                curveToRelative(-2.98f, 0.0f, -5.4f, -2.42f, -5.4f, -5.4f)
                curveToRelative(0.0f, -1.81f, 0.89f, -3.42f, 2.26f, -4.4f)
                curveTo(12.92f, 3.04f, 12.46f, 3.0f, 12.0f, 3.0f)
                lineTo(12.0f, 3.0f)
                close()
            }
        }.also { _darkMode = it }
    }

private var _darkMode: ImageVector? = null

internal val Icons.Outlined.DirectionsCar: ImageVector
    get() {
        _directionsCar?.let { return it }
        return materialIcon(name = "Outlined.DirectionsCar") {
            materialPath {
                moveTo(18.92f, 6.01f)
                curveTo(18.72f, 5.42f, 18.16f, 5.0f, 17.5f, 5.0f)
                horizontalLineToRelative(-11.0f)
                curveToRelative(-0.66f, 0.0f, -1.21f, 0.42f, -1.42f, 1.01f)
                lineTo(3.0f, 12.0f)
                verticalLineToRelative(8.0f)
                curveToRelative(0.0f, 0.55f, 0.45f, 1.0f, 1.0f, 1.0f)
                horizontalLineToRelative(1.0f)
                curveToRelative(0.55f, 0.0f, 1.0f, -0.45f, 1.0f, -1.0f)
                verticalLineToRelative(-1.0f)
                horizontalLineToRelative(12.0f)
                verticalLineToRelative(1.0f)
                curveToRelative(0.0f, 0.55f, 0.45f, 1.0f, 1.0f, 1.0f)
                horizontalLineToRelative(1.0f)
                curveToRelative(0.55f, 0.0f, 1.0f, -0.45f, 1.0f, -1.0f)
                verticalLineToRelative(-8.0f)
                lineToRelative(-2.08f, -5.99f)
                close()
                moveTo(6.85f, 7.0f)
                horizontalLineToRelative(10.29f)
                lineToRelative(1.08f, 3.11f)
                horizontalLineTo(5.77f)
                lineTo(6.85f, 7.0f)
                close()
                moveTo(19.0f, 17.0f)
                horizontalLineTo(5.0f)
                verticalLineToRelative(-5.0f)
                horizontalLineToRelative(14.0f)
                verticalLineToRelative(5.0f)
                close()
            }
            materialPath {
                moveTo(7.5f, 14.5f)
                moveToRelative(-1.5f, 0.0f)
                arcToRelative(1.5f, 1.5f, 0.0f, true, true, 3.0f, 0.0f)
                arcToRelative(1.5f, 1.5f, 0.0f, true, true, -3.0f, 0.0f)
            }
            materialPath {
                moveTo(16.5f, 14.5f)
                moveToRelative(-1.5f, 0.0f)
                arcToRelative(1.5f, 1.5f, 0.0f, true, true, 3.0f, 0.0f)
                arcToRelative(1.5f, 1.5f, 0.0f, true, true, -3.0f, 0.0f)
            }
        }.also { _directionsCar = it }
    }

private var _directionsCar: ImageVector? = null

internal val Icons.Outlined.Egg: ImageVector
    get() {
        _egg?.let { return it }
        return materialIcon(name = "Outlined.Egg") {
            materialPath {
                moveTo(12.0f, 3.0f)
                curveTo(8.5f, 3.0f, 5.0f, 9.33f, 5.0f, 14.0f)
                curveToRelative(0.0f, 3.87f, 3.13f, 7.0f, 7.0f, 7.0f)
                curveToRelative(3.87f, 0.0f, 7.0f, -3.13f, 7.0f, -7.0f)
                curveTo(19.0f, 9.33f, 15.5f, 3.0f, 12.0f, 3.0f)
                close()
                moveTo(12.0f, 19.0f)
                curveToRelative(-2.76f, 0.0f, -5.0f, -2.24f, -5.0f, -5.0f)
                curveToRelative(0.0f, -4.09f, 3.07f, -9.0f, 5.0f, -9.0f)
                reflectiveCurveToRelative(5.0f, 4.91f, 5.0f, 9.0f)
                curveTo(17.0f, 16.76f, 14.76f, 19.0f, 12.0f, 19.0f)
                close()
            }
            materialPath {
                moveTo(13.0f, 16.0f)
                curveToRelative(-0.58f, 0.0f, -3.0f, -0.08f, -3.0f, -3.0f)
                curveToRelative(0.0f, -0.55f, -0.45f, -1.0f, -1.0f, -1.0f)
                reflectiveCurveToRelative(-1.0f, 0.45f, -1.0f, 1.0f)
                curveToRelative(0.0f, 3.0f, 1.99f, 5.0f, 5.0f, 5.0f)
                curveToRelative(0.55f, 0.0f, 1.0f, -0.45f, 1.0f, -1.0f)
                reflectiveCurveTo(13.55f, 16.0f, 13.0f, 16.0f)
                close()
            }
        }.also { _egg = it }
    }

private var _egg: ImageVector? = null

internal val Icons.Outlined.FileOpen: ImageVector
    get() {
        _fileOpen?.let { return it }
        return materialIcon(name = "Outlined.FileOpen") {
            materialPath {
                moveTo(15.0f, 22.0f)
                horizontalLineTo(6.0f)
                curveToRelative(-1.1f, 0.0f, -2.0f, -0.9f, -2.0f, -2.0f)
                verticalLineTo(4.0f)
                curveToRelative(0.0f, -1.1f, 0.9f, -2.0f, 2.0f, -2.0f)
                horizontalLineToRelative(8.0f)
                lineToRelative(6.0f, 6.0f)
                verticalLineToRelative(6.0f)
                horizontalLineToRelative(-2.0f)
                verticalLineTo(9.0f)
                horizontalLineToRelative(-5.0f)
                verticalLineTo(4.0f)
                horizontalLineTo(6.0f)
                verticalLineToRelative(16.0f)
                horizontalLineToRelative(9.0f)
                verticalLineTo(22.0f)
                close()
                moveTo(19.0f, 21.66f)
                lineToRelative(0.0f, -2.24f)
                lineToRelative(2.95f, 2.95f)
                lineToRelative(1.41f, -1.41f)
                lineTo(20.41f, 18.0f)
                horizontalLineToRelative(2.24f)
                verticalLineToRelative(-2.0f)
                horizontalLineTo(17.0f)
                verticalLineToRelative(5.66f)
                horizontalLineTo(19.0f)
                close()
            }
        }.also { _fileOpen = it }
    }

private var _fileOpen: ImageVector? = null

internal val Icons.Outlined.Filter: ImageVector
    get() {
        _filter?.let { return it }
        return materialIcon(name = "Outlined.Filter") {
            materialPath {
                moveTo(15.96f, 10.29f)
                lineToRelative(-2.75f, 3.54f)
                lineToRelative(-1.96f, -2.36f)
                lineTo(8.5f, 15.0f)
                horizontalLineToRelative(11.0f)
                lineToRelative(-3.54f, -4.71f)
                close()
                moveTo(3.0f, 5.0f)
                lineTo(1.0f, 5.0f)
                verticalLineToRelative(16.0f)
                curveToRelative(0.0f, 1.1f, 0.9f, 2.0f, 2.0f, 2.0f)
                horizontalLineToRelative(16.0f)
                verticalLineToRelative(-2.0f)
                lineTo(3.0f, 21.0f)
                lineTo(3.0f, 5.0f)
                close()
                moveTo(21.0f, 1.0f)
                lineTo(7.0f, 1.0f)
                curveToRelative(-1.1f, 0.0f, -2.0f, 0.9f, -2.0f, 2.0f)
                verticalLineToRelative(14.0f)
                curveToRelative(0.0f, 1.1f, 0.9f, 2.0f, 2.0f, 2.0f)
                horizontalLineToRelative(14.0f)
                curveToRelative(1.1f, 0.0f, 2.0f, -0.9f, 2.0f, -2.0f)
                lineTo(23.0f, 3.0f)
                curveToRelative(0.0f, -1.1f, -0.9f, -2.0f, -2.0f, -2.0f)
                close()
                moveTo(21.0f, 17.0f)
                lineTo(7.0f, 17.0f)
                lineTo(7.0f, 3.0f)
                horizontalLineToRelative(14.0f)
                verticalLineToRelative(14.0f)
                close()
            }
        }.also { _filter = it }
    }

private var _filter: ImageVector? = null

internal val Icons.Outlined.FilterDrama: ImageVector
    get() {
        _filterDrama?.let { return it }
        return materialIcon(name = "Outlined.FilterDrama") {
            materialPath {
                moveTo(19.35f, 10.04f)
                curveTo(18.67f, 6.59f, 15.64f, 4.0f, 12.0f, 4.0f)
                curveTo(9.11f, 4.0f, 6.61f, 5.64f, 5.36f, 8.04f)
                curveTo(2.35f, 8.36f, 0.0f, 10.9f, 0.0f, 14.0f)
                curveToRelative(0.0f, 3.31f, 2.69f, 6.0f, 6.0f, 6.0f)
                horizontalLineToRelative(13.0f)
                curveToRelative(2.76f, 0.0f, 5.0f, -2.24f, 5.0f, -5.0f)
                curveToRelative(0.0f, -2.64f, -2.05f, -4.78f, -4.65f, -4.96f)
                close()
                moveTo(19.0f, 18.0f)
                horizontalLineTo(6.0f)
                curveToRelative(-2.21f, 0.0f, -4.0f, -1.79f, -4.0f, -4.0f)
                reflectiveCurveToRelative(1.79f, -4.0f, 4.0f, -4.0f)
                reflectiveCurveToRelative(4.0f, 1.79f, 4.0f, 4.0f)
                horizontalLineToRelative(2.0f)
                curveToRelative(0.0f, -2.76f, -1.86f, -5.08f, -4.4f, -5.78f)
                curveTo(8.61f, 6.88f, 10.2f, 6.0f, 12.0f, 6.0f)
                curveToRelative(3.03f, 0.0f, 5.5f, 2.47f, 5.5f, 5.5f)
                verticalLineToRelative(0.5f)
                horizontalLineTo(19.0f)
                curveToRelative(1.65f, 0.0f, 3.0f, 1.35f, 3.0f, 3.0f)
                reflectiveCurveToRelative(-1.35f, 3.0f, -3.0f, 3.0f)
                close()
            }
        }.also { _filterDrama = it }
    }

private var _filterDrama: ImageVector? = null

internal val Icons.Outlined.Flare: ImageVector
    get() {
        _flare?.let { return it }
        return materialIcon(name = "Outlined.Flare") {
            materialPath {
                moveTo(7.0f, 11.0f)
                lineTo(1.0f, 11.0f)
                verticalLineToRelative(2.0f)
                horizontalLineToRelative(6.0f)
                verticalLineToRelative(-2.0f)
                close()
                moveTo(9.17f, 7.76f)
                lineTo(7.05f, 5.64f)
                lineTo(5.64f, 7.05f)
                lineToRelative(2.12f, 2.12f)
                lineToRelative(1.41f, -1.41f)
                close()
                moveTo(13.0f, 1.0f)
                horizontalLineToRelative(-2.0f)
                verticalLineToRelative(6.0f)
                horizontalLineToRelative(2.0f)
                lineTo(13.0f, 1.0f)
                close()
                moveTo(18.36f, 7.05f)
                lineToRelative(-1.41f, -1.41f)
                lineToRelative(-2.12f, 2.12f)
                lineToRelative(1.41f, 1.41f)
                lineToRelative(2.12f, -2.12f)
                close()
                moveTo(17.0f, 11.0f)
                verticalLineToRelative(2.0f)
                horizontalLineToRelative(6.0f)
                verticalLineToRelative(-2.0f)
                horizontalLineToRelative(-6.0f)
                close()
                moveTo(12.0f, 9.0f)
                curveToRelative(-1.66f, 0.0f, -3.0f, 1.34f, -3.0f, 3.0f)
                reflectiveCurveToRelative(1.34f, 3.0f, 3.0f, 3.0f)
                reflectiveCurveToRelative(3.0f, -1.34f, 3.0f, -3.0f)
                reflectiveCurveToRelative(-1.34f, -3.0f, -3.0f, -3.0f)
                close()
                moveTo(14.83f, 16.24f)
                lineToRelative(2.12f, 2.12f)
                lineToRelative(1.41f, -1.41f)
                lineToRelative(-2.12f, -2.12f)
                lineToRelative(-1.41f, 1.41f)
                close()
                moveTo(5.64f, 16.95f)
                lineToRelative(1.41f, 1.41f)
                lineToRelative(2.12f, -2.12f)
                lineToRelative(-1.41f, -1.41f)
                lineToRelative(-2.12f, 2.12f)
                close()
                moveTo(11.0f, 23.0f)
                horizontalLineToRelative(2.0f)
                verticalLineToRelative(-6.0f)
                horizontalLineToRelative(-2.0f)
                verticalLineToRelative(6.0f)
                close()
            }
        }.also { _flare = it }
    }

private var _flare: ImageVector? = null

internal val Icons.Outlined.House: ImageVector
    get() {
        _house?.let { return it }
        return materialIcon(name = "Outlined.House") {
            materialPath {
                moveTo(19.0f, 9.3f)
                verticalLineTo(4.0f)
                horizontalLineToRelative(-3.0f)
                verticalLineToRelative(2.6f)
                lineTo(12.0f, 3.0f)
                lineTo(2.0f, 12.0f)
                horizontalLineToRelative(3.0f)
                verticalLineToRelative(8.0f)
                horizontalLineToRelative(6.0f)
                verticalLineToRelative(-6.0f)
                horizontalLineToRelative(2.0f)
                verticalLineToRelative(6.0f)
                horizontalLineToRelative(6.0f)
                verticalLineToRelative(-8.0f)
                horizontalLineToRelative(3.0f)
                lineTo(19.0f, 9.3f)
                close()
                moveTo(17.0f, 18.0f)
                horizontalLineToRelative(-2.0f)
                verticalLineToRelative(-6.0f)
                horizontalLineTo(9.0f)
                verticalLineToRelative(6.0f)
                horizontalLineTo(7.0f)
                verticalLineToRelative(-7.81f)
                lineToRelative(5.0f, -4.5f)
                lineToRelative(5.0f, 4.5f)
                verticalLineTo(18.0f)
                close()
            }
            materialPath {
                moveTo(10.0f, 10.0f)
                horizontalLineToRelative(4.0f)
                curveToRelative(0.0f, -1.1f, -0.9f, -2.0f, -2.0f, -2.0f)
                reflectiveCurveTo(10.0f, 8.9f, 10.0f, 10.0f)
                close()
            }
        }.also { _house = it }
    }

private var _house: ImageVector? = null

internal val Icons.Outlined.Landscape: ImageVector
    get() {
        _landscape?.let { return it }
        return materialIcon(name = "Outlined.Landscape") {
            materialPath {
                moveTo(14.0f, 6.0f)
                lineToRelative(-4.22f, 5.63f)
                lineToRelative(1.25f, 1.67f)
                lineTo(14.0f, 9.33f)
                lineTo(19.0f, 16.0f)
                horizontalLineToRelative(-8.46f)
                lineToRelative(-4.01f, -5.37f)
                lineTo(1.0f, 18.0f)
                horizontalLineToRelative(22.0f)
                lineTo(14.0f, 6.0f)
                close()
                moveTo(5.0f, 16.0f)
                lineToRelative(1.52f, -2.03f)
                lineTo(8.04f, 16.0f)
                horizontalLineTo(5.0f)
                close()
            }
        }.also { _landscape = it }
    }

private var _landscape: ImageVector? = null

internal val Icons.Outlined.LocalFlorist: ImageVector
    get() {
        _localFlorist?.let { return it }
        return materialIcon(name = "Outlined.LocalFlorist") {
            materialPath {
                moveTo(8.66f, 13.07f)
                curveToRelative(0.15f, 0.0f, 0.29f, -0.01f, 0.43f, -0.03f)
                curveTo(9.56f, 14.19f, 10.69f, 15.0f, 12.0f, 15.0f)
                reflectiveCurveToRelative(2.44f, -0.81f, 2.91f, -1.96f)
                curveToRelative(0.14f, 0.02f, 0.29f, 0.03f, 0.43f, 0.03f)
                curveToRelative(1.73f, 0.0f, 3.14f, -1.41f, 3.14f, -3.14f)
                curveToRelative(0.0f, -0.71f, -0.25f, -1.39f, -0.67f, -1.93f)
                curveToRelative(0.43f, -0.54f, 0.67f, -1.22f, 0.67f, -1.93f)
                curveToRelative(0.0f, -1.73f, -1.41f, -3.14f, -3.14f, -3.14f)
                curveToRelative(-0.15f, 0.0f, -0.29f, 0.01f, -0.43f, 0.03f)
                curveTo(14.44f, 1.81f, 13.31f, 1.0f, 12.0f, 1.0f)
                reflectiveCurveToRelative(-2.44f, 0.81f, -2.91f, 1.96f)
                curveToRelative(-0.14f, -0.02f, -0.29f, -0.03f, -0.43f, -0.03f)
                curveToRelative(-1.73f, 0.0f, -3.14f, 1.41f, -3.14f, 3.14f)
                curveToRelative(0.0f, 0.71f, 0.25f, 1.39f, 0.67f, 1.93f)
                curveToRelative(-0.43f, 0.54f, -0.68f, 1.22f, -0.68f, 1.93f)
                curveToRelative(0.0f, 1.73f, 1.41f, 3.14f, 3.15f, 3.14f)
                close()
                moveTo(12.0f, 13.0f)
                curveToRelative(-0.62f, 0.0f, -1.12f, -0.49f, -1.14f, -1.1f)
                lineToRelative(0.12f, -1.09f)
                curveToRelative(0.32f, 0.12f, 0.66f, 0.19f, 1.02f, 0.19f)
                reflectiveCurveToRelative(0.71f, -0.07f, 1.03f, -0.19f)
                lineToRelative(0.11f, 1.09f)
                curveToRelative(-0.02f, 0.61f, -0.52f, 1.1f, -1.14f, 1.1f)
                close()
                moveTo(15.34f, 11.07f)
                curveToRelative(-0.24f, 0.0f, -0.46f, -0.07f, -0.64f, -0.2f)
                lineToRelative(-0.81f, -0.57f)
                curveToRelative(0.55f, -0.45f, 0.94f, -1.09f, 1.06f, -1.83f)
                lineToRelative(0.88f, 0.42f)
                curveToRelative(0.4f, 0.19f, 0.66f, 0.59f, 0.66f, 1.03f)
                curveToRelative(0.0f, 0.64f, -0.52f, 1.15f, -1.15f, 1.15f)
                close()
                moveTo(14.69f, 5.13f)
                curveToRelative(0.2f, -0.13f, 0.42f, -0.2f, 0.65f, -0.2f)
                curveToRelative(0.63f, 0.0f, 1.14f, 0.51f, 1.14f, 1.14f)
                curveToRelative(0.0f, 0.44f, -0.25f, 0.83f, -0.66f, 1.03f)
                lineToRelative(-0.88f, 0.42f)
                curveToRelative(-0.12f, -0.74f, -0.51f, -1.38f, -1.07f, -1.83f)
                lineToRelative(0.82f, -0.56f)
                close()
                moveTo(12.0f, 3.0f)
                curveToRelative(0.62f, 0.0f, 1.12f, 0.49f, 1.14f, 1.1f)
                lineToRelative(-0.11f, 1.09f)
                curveTo(12.71f, 5.07f, 12.36f, 5.0f, 12.0f, 5.0f)
                reflectiveCurveToRelative(-0.7f, 0.07f, -1.02f, 0.19f)
                lineToRelative(-0.12f, -1.09f)
                curveToRelative(0.02f, -0.61f, 0.52f, -1.1f, 1.14f, -1.1f)
                close()
                moveTo(8.66f, 4.93f)
                curveToRelative(0.24f, 0.0f, 0.46f, 0.07f, 0.64f, 0.2f)
                lineToRelative(0.81f, 0.56f)
                curveToRelative(-0.55f, 0.45f, -0.94f, 1.09f, -1.06f, 1.83f)
                lineToRelative(-0.88f, -0.42f)
                curveToRelative(-0.4f, -0.2f, -0.66f, -0.59f, -0.66f, -1.03f)
                curveToRelative(0.0f, -0.63f, 0.52f, -1.14f, 1.15f, -1.14f)
                close()
                moveTo(8.17f, 8.9f)
                lineToRelative(0.88f, -0.42f)
                curveToRelative(0.12f, 0.74f, 0.51f, 1.38f, 1.07f, 1.83f)
                lineToRelative(-0.81f, 0.55f)
                curveToRelative(-0.2f, 0.13f, -0.42f, 0.2f, -0.65f, 0.2f)
                curveToRelative(-0.63f, 0.0f, -1.14f, -0.51f, -1.14f, -1.14f)
                curveToRelative(-0.01f, -0.43f, 0.25f, -0.82f, 0.65f, -1.02f)
                close()
                moveTo(12.0f, 22.0f)
                curveToRelative(4.97f, 0.0f, 9.0f, -4.03f, 9.0f, -9.0f)
                curveToRelative(-4.97f, 0.0f, -9.0f, 4.03f, -9.0f, 9.0f)
                close()
                moveTo(14.44f, 19.56f)
                curveToRelative(0.71f, -1.9f, 2.22f, -3.42f, 4.12f, -4.12f)
                curveToRelative(-0.71f, 1.9f, -2.22f, 3.41f, -4.12f, 4.12f)
                close()
                moveTo(3.0f, 13.0f)
                curveToRelative(0.0f, 4.97f, 4.03f, 9.0f, 9.0f, 9.0f)
                curveToRelative(0.0f, -4.97f, -4.03f, -9.0f, -9.0f, -9.0f)
                close()
                moveTo(5.44f, 15.44f)
                curveToRelative(1.9f, 0.71f, 3.42f, 2.22f, 4.12f, 4.12f)
                curveToRelative(-1.9f, -0.71f, -3.41f, -2.22f, -4.12f, -4.12f)
                close()
            }
        }.also { _localFlorist = it }
    }

private var _localFlorist: ImageVector? = null

internal val Icons.Outlined.Park: ImageVector
    get() {
        _park?.let { return it }
        return materialIcon(name = "Outlined.Park") {
            materialPath {
                moveTo(17.0f, 12.0f)
                horizontalLineToRelative(2.0f)
                lineTo(12.0f, 2.0f)
                lineTo(5.05f, 12.0f)
                horizontalLineTo(7.0f)
                lineToRelative(-3.9f, 6.0f)
                horizontalLineToRelative(6.92f)
                verticalLineToRelative(4.0f)
                horizontalLineToRelative(3.95f)
                verticalLineToRelative(-4.0f)
                horizontalLineTo(21.0f)
                lineTo(17.0f, 12.0f)
                close()
                moveTo(6.79f, 16.0f)
                lineToRelative(3.9f, -6.0f)
                horizontalLineTo(8.88f)
                lineToRelative(3.13f, -4.5f)
                lineToRelative(3.15f, 4.5f)
                horizontalLineToRelative(-1.9f)
                lineToRelative(4.0f, 6.0f)
                horizontalLineTo(6.79f)
                close()
            }
        }.also { _park = it }
    }

private var _park: ImageVector? = null

internal val Icons.Outlined.Restore: ImageVector
    get() {
        _restore?.let { return it }
        return materialIcon(name = "Outlined.Restore") {
            materialPath {
                moveTo(13.0f, 3.0f)
                curveToRelative(-4.97f, 0.0f, -9.0f, 4.03f, -9.0f, 9.0f)
                lineTo(1.0f, 12.0f)
                lineToRelative(4.0f, 3.99f)
                lineTo(9.0f, 12.0f)
                lineTo(6.0f, 12.0f)
                curveToRelative(0.0f, -3.87f, 3.13f, -7.0f, 7.0f, -7.0f)
                reflectiveCurveToRelative(7.0f, 3.13f, 7.0f, 7.0f)
                reflectiveCurveToRelative(-3.13f, 7.0f, -7.0f, 7.0f)
                curveToRelative(-1.93f, 0.0f, -3.68f, -0.79f, -4.94f, -2.06f)
                lineToRelative(-1.42f, 1.42f)
                curveTo(8.27f, 19.99f, 10.51f, 21.0f, 13.0f, 21.0f)
                curveToRelative(4.97f, 0.0f, 9.0f, -4.03f, 9.0f, -9.0f)
                reflectiveCurveToRelative(-4.03f, -9.0f, -9.0f, -9.0f)
                close()
                moveTo(12.0f, 8.0f)
                verticalLineToRelative(5.0f)
                lineToRelative(4.25f, 2.52f)
                lineToRelative(0.77f, -1.28f)
                lineToRelative(-3.52f, -2.09f)
                lineTo(13.5f, 8.0f)
                close()
            }
        }.also { _restore = it }
    }

private var _restore: ImageVector? = null

internal val Icons.Outlined.Save: ImageVector
    get() {
        _save?.let { return it }
        return materialIcon(name = "Outlined.Save") {
            materialPath {
                moveTo(17.0f, 3.0f)
                lineTo(5.0f, 3.0f)
                curveToRelative(-1.11f, 0.0f, -2.0f, 0.9f, -2.0f, 2.0f)
                verticalLineToRelative(14.0f)
                curveToRelative(0.0f, 1.1f, 0.89f, 2.0f, 2.0f, 2.0f)
                horizontalLineToRelative(14.0f)
                curveToRelative(1.1f, 0.0f, 2.0f, -0.9f, 2.0f, -2.0f)
                lineTo(21.0f, 7.0f)
                lineToRelative(-4.0f, -4.0f)
                close()
                moveTo(19.0f, 19.0f)
                lineTo(5.0f, 19.0f)
                lineTo(5.0f, 5.0f)
                horizontalLineToRelative(11.17f)
                lineTo(19.0f, 7.83f)
                lineTo(19.0f, 19.0f)
                close()
                moveTo(12.0f, 12.0f)
                curveToRelative(-1.66f, 0.0f, -3.0f, 1.34f, -3.0f, 3.0f)
                reflectiveCurveToRelative(1.34f, 3.0f, 3.0f, 3.0f)
                reflectiveCurveToRelative(3.0f, -1.34f, 3.0f, -3.0f)
                reflectiveCurveToRelative(-1.34f, -3.0f, -3.0f, -3.0f)
                close()
                moveTo(6.0f, 6.0f)
                horizontalLineToRelative(9.0f)
                verticalLineToRelative(4.0f)
                lineTo(6.0f, 10.0f)
                close()
            }
        }.also { _save = it }
    }

private var _save: ImageVector? = null

internal val Icons.Outlined.Schedule: ImageVector
    get() {
        _schedule?.let { return it }
        return materialIcon(name = "Outlined.Schedule") {
            materialPath {
                moveTo(11.99f, 2.0f)
                curveTo(6.47f, 2.0f, 2.0f, 6.48f, 2.0f, 12.0f)
                reflectiveCurveToRelative(4.47f, 10.0f, 9.99f, 10.0f)
                curveTo(17.52f, 22.0f, 22.0f, 17.52f, 22.0f, 12.0f)
                reflectiveCurveTo(17.52f, 2.0f, 11.99f, 2.0f)
                close()
                moveTo(12.0f, 20.0f)
                curveToRelative(-4.42f, 0.0f, -8.0f, -3.58f, -8.0f, -8.0f)
                reflectiveCurveToRelative(3.58f, -8.0f, 8.0f, -8.0f)
                reflectiveCurveToRelative(8.0f, 3.58f, 8.0f, 8.0f)
                reflectiveCurveToRelative(-3.58f, 8.0f, -8.0f, 8.0f)
                close()
                moveTo(12.5f, 7.0f)
                lineTo(11.0f, 7.0f)
                verticalLineToRelative(6.0f)
                lineToRelative(5.25f, 3.15f)
                lineToRelative(0.75f, -1.23f)
                lineToRelative(-4.5f, -2.67f)
                close()
            }
        }.also { _schedule = it }
    }

private var _schedule: ImageVector? = null

internal val Icons.Outlined.StarBorder: ImageVector
    get() {
        _starBorder?.let { return it }
        return materialIcon(name = "Outlined.StarBorder") {
            materialPath {
                moveTo(22.0f, 9.24f)
                lineToRelative(-7.19f, -0.62f)
                lineTo(12.0f, 2.0f)
                lineTo(9.19f, 8.63f)
                lineTo(2.0f, 9.24f)
                lineToRelative(5.46f, 4.73f)
                lineTo(5.82f, 21.0f)
                lineTo(12.0f, 17.27f)
                lineTo(18.18f, 21.0f)
                lineToRelative(-1.63f, -7.03f)
                lineTo(22.0f, 9.24f)
                close()
                moveTo(12.0f, 15.4f)
                lineToRelative(-3.76f, 2.27f)
                lineToRelative(1.0f, -4.28f)
                lineToRelative(-3.32f, -2.88f)
                lineToRelative(4.38f, -0.38f)
                lineTo(12.0f, 6.1f)
                lineToRelative(1.71f, 4.04f)
                lineToRelative(4.38f, 0.38f)
                lineToRelative(-3.32f, 2.88f)
                lineToRelative(1.0f, 4.28f)
                lineTo(12.0f, 15.4f)
                close()
            }
        }.also { _starBorder = it }
    }

private var _starBorder: ImageVector? = null

internal val Icons.Outlined.Storefront: ImageVector
    get() {
        _storefront?.let { return it }
        return materialIcon(name = "Outlined.Storefront") {
            materialPath {
                moveTo(21.9f, 8.89f)
                lineToRelative(-1.05f, -4.37f)
                curveToRelative(-0.22f, -0.9f, -1.0f, -1.52f, -1.91f, -1.52f)
                horizontalLineTo(5.05f)
                curveTo(4.15f, 3.0f, 3.36f, 3.63f, 3.15f, 4.52f)
                lineTo(2.1f, 8.89f)
                curveToRelative(-0.24f, 1.02f, -0.02f, 2.06f, 0.62f, 2.88f)
                curveTo(2.8f, 11.88f, 2.91f, 11.96f, 3.0f, 12.06f)
                verticalLineTo(19.0f)
                curveToRelative(0.0f, 1.1f, 0.9f, 2.0f, 2.0f, 2.0f)
                horizontalLineToRelative(14.0f)
                curveToRelative(1.1f, 0.0f, 2.0f, -0.9f, 2.0f, -2.0f)
                verticalLineToRelative(-6.94f)
                curveToRelative(0.09f, -0.09f, 0.2f, -0.18f, 0.28f, -0.28f)
                curveTo(21.92f, 10.96f, 22.15f, 9.91f, 21.9f, 8.89f)
                close()
                moveTo(18.91f, 4.99f)
                lineToRelative(1.05f, 4.37f)
                curveToRelative(0.1f, 0.42f, 0.01f, 0.84f, -0.25f, 1.17f)
                curveTo(19.57f, 10.71f, 19.27f, 11.0f, 18.77f, 11.0f)
                curveToRelative(-0.61f, 0.0f, -1.14f, -0.49f, -1.21f, -1.14f)
                lineTo(16.98f, 5.0f)
                lineTo(18.91f, 4.99f)
                close()
                moveTo(13.0f, 5.0f)
                horizontalLineToRelative(1.96f)
                lineToRelative(0.54f, 4.52f)
                curveToRelative(0.05f, 0.39f, -0.07f, 0.78f, -0.33f, 1.07f)
                curveTo(14.95f, 10.85f, 14.63f, 11.0f, 14.22f, 11.0f)
                curveTo(13.55f, 11.0f, 13.0f, 10.41f, 13.0f, 9.69f)
                verticalLineTo(5.0f)
                close()
                moveTo(8.49f, 9.52f)
                lineTo(9.04f, 5.0f)
                horizontalLineTo(11.0f)
                verticalLineToRelative(4.69f)
                curveTo(11.0f, 10.41f, 10.45f, 11.0f, 9.71f, 11.0f)
                curveToRelative(-0.34f, 0.0f, -0.65f, -0.15f, -0.89f, -0.41f)
                curveTo(8.57f, 10.3f, 8.45f, 9.91f, 8.49f, 9.52f)
                close()
                moveTo(4.04f, 9.36f)
                lineTo(5.05f, 5.0f)
                horizontalLineToRelative(1.97f)
                lineTo(6.44f, 9.86f)
                curveTo(6.36f, 10.51f, 5.84f, 11.0f, 5.23f, 11.0f)
                curveToRelative(-0.49f, 0.0f, -0.8f, -0.29f, -0.93f, -0.47f)
                curveTo(4.03f, 10.21f, 3.94f, 9.78f, 4.04f, 9.36f)
                close()
                moveTo(5.0f, 19.0f)
                verticalLineToRelative(-6.03f)
                curveTo(5.08f, 12.98f, 5.15f, 13.0f, 5.23f, 13.0f)
                curveToRelative(0.87f, 0.0f, 1.66f, -0.36f, 2.24f, -0.95f)
                curveToRelative(0.6f, 0.6f, 1.4f, 0.95f, 2.31f, 0.95f)
                curveToRelative(0.87f, 0.0f, 1.65f, -0.36f, 2.23f, -0.93f)
                curveToRelative(0.59f, 0.57f, 1.39f, 0.93f, 2.29f, 0.93f)
                curveToRelative(0.84f, 0.0f, 1.64f, -0.35f, 2.24f, -0.95f)
                curveToRelative(0.58f, 0.59f, 1.37f, 0.95f, 2.24f, 0.95f)
                curveToRelative(0.08f, 0.0f, 0.15f, -0.02f, 0.23f, -0.03f)
                verticalLineTo(19.0f)
                horizontalLineTo(5.0f)
                close()
            }
        }.also { _storefront = it }
    }

private var _storefront: ImageVector? = null

internal val Icons.Outlined.SystemUpdate: ImageVector
    get() {
        _systemUpdate?.let { return it }
        return materialIcon(name = "Outlined.SystemUpdate") {
            materialPath {
                moveTo(17.0f, 1.01f)
                lineTo(7.0f, 1.0f)
                curveToRelative(-1.1f, 0.0f, -2.0f, 0.9f, -2.0f, 2.0f)
                verticalLineToRelative(18.0f)
                curveToRelative(0.0f, 1.1f, 0.9f, 2.0f, 2.0f, 2.0f)
                horizontalLineToRelative(10.0f)
                curveToRelative(1.1f, 0.0f, 2.0f, -0.9f, 2.0f, -2.0f)
                lineTo(19.0f, 3.0f)
                curveToRelative(0.0f, -1.1f, -0.9f, -1.99f, -2.0f, -1.99f)
                close()
                moveTo(17.0f, 19.0f)
                lineTo(7.0f, 19.0f)
                lineTo(7.0f, 5.0f)
                horizontalLineToRelative(10.0f)
                verticalLineToRelative(14.0f)
                close()
                moveTo(16.0f, 13.0f)
                horizontalLineToRelative(-3.0f)
                lineTo(13.0f, 8.0f)
                horizontalLineToRelative(-2.0f)
                verticalLineToRelative(5.0f)
                lineTo(8.0f, 13.0f)
                lineToRelative(4.0f, 4.0f)
                lineToRelative(4.0f, -4.0f)
                close()
            }
        }.also { _systemUpdate = it }
    }

private var _systemUpdate: ImageVector? = null

internal val Icons.Outlined.Terrain: ImageVector
    get() {
        _terrain?.let { return it }
        return materialIcon(name = "Outlined.Terrain") {
            materialPath {
                moveTo(14.0f, 6.0f)
                lineToRelative(-4.22f, 5.63f)
                lineToRelative(1.25f, 1.67f)
                lineTo(14.0f, 9.33f)
                lineTo(19.0f, 16.0f)
                horizontalLineToRelative(-8.46f)
                lineToRelative(-4.01f, -5.37f)
                lineTo(1.0f, 18.0f)
                horizontalLineToRelative(22.0f)
                lineTo(14.0f, 6.0f)
                close()
                moveTo(5.0f, 16.0f)
                lineToRelative(1.52f, -2.03f)
                lineTo(8.04f, 16.0f)
                horizontalLineTo(5.0f)
                close()
            }
        }.also { _terrain = it }
    }

private var _terrain: ImageVector? = null

internal val Icons.Outlined.WaterDrop: ImageVector
    get() {
        _waterDrop?.let { return it }
        return materialIcon(name = "Outlined.WaterDrop") {
            materialPath {
                moveTo(12.0f, 2.0f)
                curveToRelative(-5.33f, 4.55f, -8.0f, 8.48f, -8.0f, 11.8f)
                curveToRelative(0.0f, 4.98f, 3.8f, 8.2f, 8.0f, 8.2f)
                reflectiveCurveToRelative(8.0f, -3.22f, 8.0f, -8.2f)
                curveTo(20.0f, 10.48f, 17.33f, 6.55f, 12.0f, 2.0f)
                close()
                moveTo(12.0f, 20.0f)
                curveToRelative(-3.35f, 0.0f, -6.0f, -2.57f, -6.0f, -6.2f)
                curveToRelative(0.0f, -2.34f, 1.95f, -5.44f, 6.0f, -9.14f)
                curveToRelative(4.05f, 3.7f, 6.0f, 6.79f, 6.0f, 9.14f)
                curveTo(18.0f, 17.43f, 15.35f, 20.0f, 12.0f, 20.0f)
                close()
                moveTo(7.83f, 14.0f)
                curveToRelative(0.37f, 0.0f, 0.67f, 0.26f, 0.74f, 0.62f)
                curveToRelative(0.41f, 2.22f, 2.28f, 2.98f, 3.64f, 2.87f)
                curveToRelative(0.43f, -0.02f, 0.79f, 0.32f, 0.79f, 0.75f)
                curveToRelative(0.0f, 0.4f, -0.32f, 0.73f, -0.72f, 0.75f)
                curveToRelative(-2.13f, 0.13f, -4.62f, -1.09f, -5.19f, -4.12f)
                curveTo(7.01f, 14.42f, 7.37f, 14.0f, 7.83f, 14.0f)
                close()
            }
        }.also { _waterDrop = it }
    }

private var _waterDrop: ImageVector? = null

internal val Icons.Outlined.Waves: ImageVector
    get() {
        _waves?.let { return it }
        return materialIcon(name = "Outlined.Waves") {
            materialPath {
                moveTo(17.0f, 16.99f)
                curveToRelative(-1.35f, 0.0f, -2.2f, 0.42f, -2.95f, 0.8f)
                curveToRelative(-0.65f, 0.33f, -1.18f, 0.6f, -2.05f, 0.6f)
                curveToRelative(-0.9f, 0.0f, -1.4f, -0.25f, -2.05f, -0.6f)
                curveToRelative(-0.75f, -0.38f, -1.57f, -0.8f, -2.95f, -0.8f)
                reflectiveCurveToRelative(-2.2f, 0.42f, -2.95f, 0.8f)
                curveToRelative(-0.65f, 0.33f, -1.17f, 0.6f, -2.05f, 0.6f)
                verticalLineToRelative(1.95f)
                curveToRelative(1.35f, 0.0f, 2.2f, -0.42f, 2.95f, -0.8f)
                curveToRelative(0.65f, -0.33f, 1.17f, -0.6f, 2.05f, -0.6f)
                reflectiveCurveToRelative(1.4f, 0.25f, 2.05f, 0.6f)
                curveToRelative(0.75f, 0.38f, 1.57f, 0.8f, 2.95f, 0.8f)
                reflectiveCurveToRelative(2.2f, -0.42f, 2.95f, -0.8f)
                curveToRelative(0.65f, -0.33f, 1.18f, -0.6f, 2.05f, -0.6f)
                curveToRelative(0.9f, 0.0f, 1.4f, 0.25f, 2.05f, 0.6f)
                curveToRelative(0.75f, 0.38f, 1.58f, 0.8f, 2.95f, 0.8f)
                verticalLineToRelative(-1.95f)
                curveToRelative(-0.9f, 0.0f, -1.4f, -0.25f, -2.05f, -0.6f)
                curveToRelative(-0.75f, -0.38f, -1.6f, -0.8f, -2.95f, -0.8f)
                close()
                moveTo(17.0f, 12.54f)
                curveToRelative(-1.35f, 0.0f, -2.2f, 0.43f, -2.95f, 0.8f)
                curveToRelative(-0.65f, 0.32f, -1.18f, 0.6f, -2.05f, 0.6f)
                curveToRelative(-0.9f, 0.0f, -1.4f, -0.25f, -2.05f, -0.6f)
                curveToRelative(-0.75f, -0.38f, -1.57f, -0.8f, -2.95f, -0.8f)
                reflectiveCurveToRelative(-2.2f, 0.43f, -2.95f, 0.8f)
                curveToRelative(-0.65f, 0.32f, -1.17f, 0.6f, -2.05f, 0.6f)
                verticalLineToRelative(1.95f)
                curveToRelative(1.35f, 0.0f, 2.2f, -0.43f, 2.95f, -0.8f)
                curveToRelative(0.65f, -0.35f, 1.15f, -0.6f, 2.05f, -0.6f)
                reflectiveCurveToRelative(1.4f, 0.25f, 2.05f, 0.6f)
                curveToRelative(0.75f, 0.38f, 1.57f, 0.8f, 2.95f, 0.8f)
                reflectiveCurveToRelative(2.2f, -0.43f, 2.95f, -0.8f)
                curveToRelative(0.65f, -0.35f, 1.15f, -0.6f, 2.05f, -0.6f)
                reflectiveCurveToRelative(1.4f, 0.25f, 2.05f, 0.6f)
                curveToRelative(0.75f, 0.38f, 1.58f, 0.8f, 2.95f, 0.8f)
                verticalLineToRelative(-1.95f)
                curveToRelative(-0.9f, 0.0f, -1.4f, -0.25f, -2.05f, -0.6f)
                curveToRelative(-0.75f, -0.38f, -1.6f, -0.8f, -2.95f, -0.8f)
                close()
                moveTo(19.95f, 4.46f)
                curveToRelative(-0.75f, -0.38f, -1.58f, -0.8f, -2.95f, -0.8f)
                reflectiveCurveToRelative(-2.2f, 0.42f, -2.95f, 0.8f)
                curveToRelative(-0.65f, 0.32f, -1.18f, 0.6f, -2.05f, 0.6f)
                curveToRelative(-0.9f, 0.0f, -1.4f, -0.25f, -2.05f, -0.6f)
                curveToRelative(-0.75f, -0.37f, -1.57f, -0.8f, -2.95f, -0.8f)
                reflectiveCurveToRelative(-2.2f, 0.42f, -2.95f, 0.8f)
                curveToRelative(-0.65f, 0.33f, -1.17f, 0.6f, -2.05f, 0.6f)
                verticalLineToRelative(1.93f)
                curveToRelative(1.35f, 0.0f, 2.2f, -0.43f, 2.95f, -0.8f)
                curveToRelative(0.65f, -0.33f, 1.17f, -0.6f, 2.05f, -0.6f)
                reflectiveCurveToRelative(1.4f, 0.25f, 2.05f, 0.6f)
                curveToRelative(0.75f, 0.38f, 1.57f, 0.8f, 2.95f, 0.8f)
                reflectiveCurveToRelative(2.2f, -0.43f, 2.95f, -0.8f)
                curveToRelative(0.65f, -0.32f, 1.18f, -0.6f, 2.05f, -0.6f)
                curveToRelative(0.9f, 0.0f, 1.4f, 0.25f, 2.05f, 0.6f)
                curveToRelative(0.75f, 0.38f, 1.58f, 0.8f, 2.95f, 0.8f)
                lineTo(22.0f, 5.04f)
                curveToRelative(-0.9f, 0.0f, -1.4f, -0.25f, -2.05f, -0.58f)
                close()
                moveTo(17.0f, 8.09f)
                curveToRelative(-1.35f, 0.0f, -2.2f, 0.43f, -2.95f, 0.8f)
                curveToRelative(-0.65f, 0.35f, -1.15f, 0.6f, -2.05f, 0.6f)
                reflectiveCurveToRelative(-1.4f, -0.25f, -2.05f, -0.6f)
                curveToRelative(-0.75f, -0.38f, -1.57f, -0.8f, -2.95f, -0.8f)
                reflectiveCurveToRelative(-2.2f, 0.43f, -2.95f, 0.8f)
                curveToRelative(-0.65f, 0.35f, -1.15f, 0.6f, -2.05f, 0.6f)
                verticalLineToRelative(1.95f)
                curveToRelative(1.35f, 0.0f, 2.2f, -0.43f, 2.95f, -0.8f)
                curveToRelative(0.65f, -0.32f, 1.18f, -0.6f, 2.05f, -0.6f)
                reflectiveCurveToRelative(1.4f, 0.25f, 2.05f, 0.6f)
                curveToRelative(0.75f, 0.38f, 1.57f, 0.8f, 2.95f, 0.8f)
                reflectiveCurveToRelative(2.2f, -0.43f, 2.95f, -0.8f)
                curveToRelative(0.65f, -0.32f, 1.18f, -0.6f, 2.05f, -0.6f)
                curveToRelative(0.9f, 0.0f, 1.4f, 0.25f, 2.05f, 0.6f)
                curveToRelative(0.75f, 0.38f, 1.58f, 0.8f, 2.95f, 0.8f)
                lineTo(22.0f, 9.49f)
                curveToRelative(-0.9f, 0.0f, -1.4f, -0.25f, -2.05f, -0.6f)
                curveToRelative(-0.75f, -0.38f, -1.6f, -0.8f, -2.95f, -0.8f)
                close()
            }
        }.also { _waves = it }
    }

private var _waves: ImageVector? = null

internal val Icons.Outlined.WbSunny: ImageVector
    get() {
        _wbSunny?.let { return it }
        return materialIcon(name = "Outlined.WbSunny") {
            materialPath {
                moveTo(6.76f, 4.84f)
                lineToRelative(-1.8f, -1.79f)
                lineToRelative(-1.41f, 1.41f)
                lineToRelative(1.79f, 1.79f)
                close()
                moveTo(1.0f, 10.5f)
                horizontalLineToRelative(3.0f)
                verticalLineToRelative(2.0f)
                lineTo(1.0f, 12.5f)
                close()
                moveTo(11.0f, 0.55f)
                horizontalLineToRelative(2.0f)
                lineTo(13.0f, 3.5f)
                horizontalLineToRelative(-2.0f)
                close()
                moveTo(19.04f, 3.045f)
                lineToRelative(1.408f, 1.407f)
                lineToRelative(-1.79f, 1.79f)
                lineToRelative(-1.407f, -1.408f)
                close()
                moveTo(17.24f, 18.16f)
                lineToRelative(1.79f, 1.8f)
                lineToRelative(1.41f, -1.41f)
                lineToRelative(-1.8f, -1.79f)
                close()
                moveTo(20.0f, 10.5f)
                horizontalLineToRelative(3.0f)
                verticalLineToRelative(2.0f)
                horizontalLineToRelative(-3.0f)
                close()
                moveTo(12.0f, 5.5f)
                curveToRelative(-3.31f, 0.0f, -6.0f, 2.69f, -6.0f, 6.0f)
                reflectiveCurveToRelative(2.69f, 6.0f, 6.0f, 6.0f)
                reflectiveCurveToRelative(6.0f, -2.69f, 6.0f, -6.0f)
                reflectiveCurveToRelative(-2.69f, -6.0f, -6.0f, -6.0f)
                close()
                moveTo(12.0f, 15.5f)
                curveToRelative(-2.21f, 0.0f, -4.0f, -1.79f, -4.0f, -4.0f)
                reflectiveCurveToRelative(1.79f, -4.0f, 4.0f, -4.0f)
                reflectiveCurveToRelative(4.0f, 1.79f, 4.0f, 4.0f)
                reflectiveCurveToRelative(-1.79f, 4.0f, -4.0f, 4.0f)
                close()
                moveTo(11.0f, 19.5f)
                horizontalLineToRelative(2.0f)
                verticalLineToRelative(2.95f)
                horizontalLineToRelative(-2.0f)
                close()
                moveTo(3.55f, 18.54f)
                lineToRelative(1.41f, 1.41f)
                lineToRelative(1.79f, -1.8f)
                lineToRelative(-1.41f, -1.41f)
                close()
            }
        }.also { _wbSunny = it }
    }

private var _wbSunny: ImageVector? = null
