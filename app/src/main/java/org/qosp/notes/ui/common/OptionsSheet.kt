package org.qosp.notes.ui.common

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.annotation.DrawableRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentManager
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import org.qosp.notes.R
import org.qosp.notes.ui.theme.QuillpadTheme

/**
 * Black Pearl: a Material 3 bottom sheet drawn with Compose (round quick actions, rounded groups, switches,
 * segmented choices). The content is a lambda, so if Android recreates the screen the sheet simply closes.
 */
class OptionsSheet : BottomSheetDialogFragment() {
    private var content: (@Composable () -> Unit)? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (content == null) dismissAllowingStateLoss()
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View =
        ComposeView(requireContext()).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                QuillpadTheme {
                    CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onSurface) {
                        Column(Modifier.verticalScroll(rememberScrollState()).navigationBarsPadding().padding(bottom = 16.dp)) {
                            Box( // drag handle
                                Modifier.align(Alignment.CenterHorizontally).padding(vertical = 16.dp).size(32.dp, 4.dp)
                                    .clip(CircleShape).background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f))
                            )
                            content?.invoke()
                        }
                    }
                }
            }
        }

    override fun onStart() {
        super.onStart()
        (dialog as? BottomSheetDialog)?.behavior?.apply {
            skipCollapsed = true
            state = BottomSheetBehavior.STATE_EXPANDED
        }
    }

    companion object {
        /** [content] gets a function that closes the sheet. */
        fun show(fragmentManager: FragmentManager, content: @Composable (close: () -> Unit) -> Unit) {
            if (fragmentManager.isStateSaved) return
            val sheet = OptionsSheet()
            sheet.content = { content { sheet.dismissAllowingStateLoss() } }
            sheet.show(fragmentManager, "options")
        }
    }
}

class SheetAction(@DrawableRes val icon: Int, val label: String, val onClick: () -> Unit)

@Composable
fun SheetTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleMedium,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, bottom = 12.dp),
    )
}

/** Up to four big round buttons with a label under each. */
@Composable
fun QuickActions(actions: List<SheetAction>) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
        actions.forEach { action ->
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(20.dp))
                    .clickable(role = Role.Button, onClick = action.onClick)
                    .padding(vertical = 8.dp),
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.size(56.dp).clip(CircleShape).background(MaterialTheme.colorScheme.secondaryContainer),
                ) {
                    Icon(painterResource(action.icon), null, tint = MaterialTheme.colorScheme.onSecondaryContainer)
                }
                Spacer(Modifier.height(6.dp))
                Text(action.label, style = MaterialTheme.typography.labelMedium, textAlign = TextAlign.Center, maxLines = 2)
            }
        }
    }
}

/** Rows grouped in one rounded card. */
@Composable
fun SheetGroup(content: @Composable ColumnScope.() -> Unit) {
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
    ) {
        Column(Modifier.padding(vertical = 6.dp), content = content)
    }
}

@Composable
fun SheetRow(@DrawableRes icon: Int, label: String, color: Color = Color.Unspecified, onClick: () -> Unit) {
    RowShell(Modifier.clickable(role = Role.Button, onClick = onClick)) {
        Icon(painterResource(icon), null, tint = if (color.isSpecified) color else MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(16.dp))
        Text(label, style = MaterialTheme.typography.bodyLarge, color = if (color.isSpecified) color else MaterialTheme.colorScheme.onSurface)
    }
}

@Composable
fun SheetSwitch(@DrawableRes icon: Int, label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    RowShell(Modifier.toggleable(value = checked, role = Role.Switch, onValueChange = onChange)) {
        Icon(painterResource(icon), null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(16.dp))
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = null)
    }
}

/** A closed section ("More settings") that opens in place. */
@Composable
fun SheetExpandable(label: String, content: @Composable ColumnScope.() -> Unit) {
    var open by rememberSaveable { mutableStateOf(false) }
    Column(Modifier.padding(horizontal = 16.dp)) {
        RowShell(Modifier.clickable(role = Role.Button) { open = !open }, horizontal = 4.dp) {
            Text(label, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
            Icon(painterResource(R.drawable.ic_down), null, modifier = Modifier.rotate(if (open) 180f else 0f))
        }
        AnimatedVisibility(open) {
            Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh) {
                Column(Modifier.padding(vertical = 6.dp), content = content)
            }
        }
    }
}

/** A labelled single choice (segmented buttons), e.g. alignment or size of a picture. */
@Composable
fun SheetChoice(label: String, options: List<Pair<String, Int?>>, selected: Int, onSelect: (Int) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)) {
        Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 8.dp, bottom = 6.dp))
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            options.forEachIndexed { index, (text, icon) ->
                SegmentedButton(
                    selected = index == selected,
                    onClick = { onSelect(index) },
                    shape = SegmentedButtonDefaults.itemShape(index, options.size),
                    icon = {},
                    label = {
                        if (icon != null) Icon(painterResource(icon), contentDescription = text, modifier = Modifier.size(20.dp))
                        else Text(text)
                    },
                )
            }
        }
    }
}

@Composable
private fun RowShell(modifier: Modifier, horizontal: Dp = 6.dp, content: @Composable RowScope.() -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = horizontal)
            .clip(RoundedCornerShape(18.dp))
            .then(modifier)
            .heightIn(min = 52.dp)
            .padding(horizontal = 14.dp),
    ) { content() }
}
