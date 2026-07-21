package com.dccbigfred.android.ui.components

import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/** Extra inset so TopAppBar actions stay reachable in immersive/fullscreen mode. */
val TopAppBarEdgePadding = 5.dp

fun Modifier.topAppBarEdgePadding(): Modifier =
    padding(start = TopAppBarEdgePadding, end = TopAppBarEdgePadding, top = TopAppBarEdgePadding)
