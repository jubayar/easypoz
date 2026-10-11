package net.redngreen.easypos.ui.catalog

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import net.redngreen.easypos.R
import net.redngreen.easypos.core.database.entity.Product
import net.redngreen.easypos.core.money.MoneyFormatter
import net.redngreen.easypos.theme.EasyPOSTheme

/**
 * Product tile (product-catalog spec): photo or a generated identity
 * (emoji, else initial over the product/category color), name, price, and
 * an optional stock badge for the sell grid ("Baki 14" / "Baki 4 · Low").
 * Stock display never gates tapping (task 7.10 wires the badge).
 * Long-press opens the quick price edit (task 6.7).
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ProductTile(
    product: Product,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    showStock: Boolean = false,
    onLongClick: (() -> Unit)? = null,
) {
    Card(
        modifier =
            modifier.height(128.dp).combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick,
            ),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(contentAlignment = Alignment.Center) {
                Surface(
                    shape = CircleShape,
                    color = tileColor(product.color),
                    modifier = Modifier.size(44.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = product.emoji ?: product.name.take(1).uppercase(),
                            style = MaterialTheme.typography.titleLarge,
                        )
                    }
                }
            }
            Text(
                text = product.name,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 6.dp, bottom = 2.dp),
            )
            Text(
                text = MoneyFormatter.format(product.priceCents),
                style = MaterialTheme.typography.labelLarge,
            )
            if (showStock) {
                val low = product.stockOnHand <= product.lowStockThreshold
                Text(
                    text = if (low) {
                        stringResource(R.string.stock_on_hand_low, product.stockOnHand)
                    } else {
                        stringResource(R.string.stock_on_hand, product.stockOnHand)
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = if (low) MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** "RRGGBB" (stored) → Color; anything unparseable falls back to theme. */
@Composable
private fun tileColor(colorHex: String?): Color {
    val fallback = MaterialTheme.colorScheme.primaryContainer
    if (colorHex == null) return fallback
    return runCatching { Color(android.graphics.Color.parseColor("#$colorHex")) }
        .getOrDefault(fallback)
}

@Preview(showBackground = true)
@Composable
private fun ProductTilePreview() {
    EasyPOSTheme {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(8.dp),
        ) {
            ProductTile(
                product = Product(
                    id = "1", shopId = "s", categoryId = null,
                    name = "Milo Activ-Go 1kg", barcode = null, sku = null,
                    costCents = 950, priceCents = 1200,
                    emoji = "🥤", color = null,
                ),
                onClick = {},
                modifier = Modifier.size(128.dp),
            )
            ProductTile(
                product = Product(
                    id = "2", shopId = "s", categoryId = null,
                    name = "Dabur Red Paste 200g", barcode = null, sku = null,
                    costCents = 420, priceCents = 590,
                    emoji = null, color = "E57373",
                    stockOnHand = 4, lowStockThreshold = 5,
                ),
                onClick = {},
                showStock = true,
                modifier = Modifier.size(128.dp),
            )
        }
    }
}
