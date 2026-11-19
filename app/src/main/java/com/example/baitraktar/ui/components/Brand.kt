package com.example.baitraktar.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.baitraktar.R

/** The K tile — the app icon, used as the mark everywhere in the app. */
@Composable
fun Logo(
    modifier: Modifier = Modifier,
    size: Dp = 32.dp,
    colorFilter: ColorFilter? = null,
) {
    Image(
        painter = painterResource(R.drawable.logo),
        contentDescription = "K",
        colorFilter = colorFilter,
        modifier = modifier.size(size),
    )
}
