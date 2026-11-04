package com.omnibuds.android.ui.compose.components

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.omnibuds.android.presentation.workspace.ProductCatalog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * The visual identity of a device, in strict precedence order:
 *
 * 1. The user's own photo of their device ([userPhotoPath]) — a genuine
 *    image of the exact physical product.
 * 2. A bundled licensed photograph from the catalog (provenance and
 *    attribution live on the catalog entry; the hero shows the
 *    attribution line beside the image).
 * 3. A neutral vector category artwork — drawn, clearly not a
 *    photograph, and never a substitute product.
 *
 * Photographs are shown whole ([ContentScale.Fit] by default): a real
 * product photo is never cropped into a different-looking product.
 *
 * [grayscale] applies the disconnected treatment to photographs.
 */
@Composable
fun ProductVisual(
    product: ProductCatalog.ProductInfo,
    userPhotoPath: String?,
    grayscale: Boolean,
    modifier: Modifier = Modifier,
    photoContentScale: ContentScale = ContentScale.Fit,
) {
    if (userPhotoPath != null) {
        val bitmap by rememberFileBitmap(userPhotoPath)
        val loaded = bitmap
        if (loaded != null) {
            Image(
                bitmap = loaded,
                contentDescription = null,
                contentScale = photoContentScale,
                colorFilter = if (grayscale) grayscaleFilter() else null,
                modifier = modifier,
            )
            return
        }
    }
    val assetName = product.imageAsset
    if (assetName != null) {
        val context = LocalContext.current
        val resId = rememberDrawableId(assetName)
        if (resId != 0) {
            Image(
                painter = painterResource(id = resId),
                contentDescription = null,
                contentScale = photoContentScale,
                colorFilter = if (grayscale) grayscaleFilter() else null,
                modifier = modifier.background(MaterialTheme.colorScheme.surfaceVariant),
            )
            return
        }
    }
    CategoryArtwork(category = product.category, modifier = modifier)
}

/** Neutral drawn representation of a device category — never a fake photo. */
@Composable
fun CategoryArtwork(
    category: ProductCatalog.Category,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier.background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = when (category) {
                ProductCatalog.Category.TWS -> OmniIcons.BudsPair
                ProductCatalog.Category.OVER_EAR -> OmniIcons.Headphones
                ProductCatalog.Category.NECKBAND -> OmniIcons.Neckband
                ProductCatalog.Category.UNKNOWN -> OmniIcons.Headphones
            },
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(84.dp),
        )
    }
}

@Composable
private fun rememberDrawableId(assetName: String): Int {
    val context = LocalContext.current
    return androidx.compose.runtime.remember(assetName) {
        context.resources.getIdentifier(assetName, "drawable", context.packageName)
    }
}

@Composable
private fun rememberFileBitmap(path: String) = produceState<ImageBitmap?>(
    initialValue = null,
    key1 = path,
) {
    value = withContext(Dispatchers.IO) {
        runCatching {
            val options = BitmapFactory.Options().apply { inSampleSize = 2 }
            BitmapFactory.decodeFile(path, options)?.asImageBitmap()
        }.getOrNull()
    }
}

private fun grayscaleFilter(): ColorFilter {
    val matrix = ColorMatrix().apply { setToSaturation(0f) }
    return ColorFilter.colorMatrix(matrix)
}
