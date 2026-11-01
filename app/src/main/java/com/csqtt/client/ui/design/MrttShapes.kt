package com.csqtt.client.ui.design

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.runtime.Immutable
import androidx.compose.ui.unit.dp

@Immutable
object MrttShapes {
    val Small = RoundedCornerShape(22.dp)
    val Control = RoundedCornerShape(percent = 50)
    val Card = RoundedCornerShape(34.dp)
    val LargeCard = RoundedCornerShape(42.dp)
    val Dialog = RoundedCornerShape(40.dp)
    val Pill = RoundedCornerShape(percent = 50)
}

val MrttMaterialShapes = Shapes(
    extraSmall = MrttShapes.Small,
    small = MrttShapes.Small,
    medium = MrttShapes.Control,
    large = MrttShapes.Card,
    extraLarge = MrttShapes.Dialog,
)
