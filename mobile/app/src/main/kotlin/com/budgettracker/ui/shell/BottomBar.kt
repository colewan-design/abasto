package com.budgettracker.ui.shell

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.budgettracker.ui.navigation.Tab
import com.budgettracker.ui.theme.accents

/**
 * The bar along the bottom: five destinations, and the accent marking which
 * one you are on.
 *
 * The labels are gone from the bar itself and live on each icon as its
 * content description, because five labels at a readable size do not fit a
 * phone's width without truncating — the filled accent square is what names
 * the current tab, and it is far easier to find at a glance than a bolded
 * eleven-point word.
 */
@Composable
fun AppBottomBar(
    current: Tab?,
    onSelect: (Tab) -> Unit,
    modifier: Modifier = Modifier,
) {
    val accents = MaterialTheme.accents

    Surface(
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = if (accents.dark) 0.dp else 12.dp,
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Tab.entries.forEach { tab ->
                val selected = tab == current
                val container by animateColorAsState(
                    targetValue = if (selected) accents.accent else Color.Transparent,
                    label = "tab-container",
                )
                val content by animateColorAsState(
                    targetValue = when {
                        selected -> accents.onAccent
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    label = "tab-content",
                )

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 4.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Box(
                        modifier = Modifier
                            .size(46.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(container)
                            .selectable(
                                selected = selected,
                                role = Role.Tab,
                                onClick = { onSelect(tab) },
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = tab.icon,
                            contentDescription = tab.label,
                            tint = content,
                            modifier = Modifier.size(22.dp),
                        )
                    }
                }
            }
        }
    }
}
