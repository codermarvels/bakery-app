package com.vsjbakery.app.ui.admin

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Storefront
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vsjbakery.app.domain.model.AdminCategory
import com.vsjbakery.app.domain.model.AdminInput
import com.vsjbakery.app.domain.model.CategoryDraft
import com.vsjbakery.app.domain.model.Money
import com.vsjbakery.app.domain.model.ProductDraft
import com.vsjbakery.app.ui.components.BackTopBar
import com.vsjbakery.app.ui.components.MessageCard
import com.vsjbakery.app.ui.components.SubmitButton
import com.vsjbakery.app.ui.shop.ProductImage
import java.io.ByteArrayOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val PRODUCTS_INFO =
    "Add and edit what the bakery sells.\n\n" +
        "• Price is per item in rupees (e.g. 45 or 45.50).\n" +
        "• Photo: square or landscape photos look best. They are resized automatically.\n" +
        "• 'Show in app' off hides a product from customers without deleting its history.\n" +
        "• Stock is not set here: use the Stock tab (+ Made) after adding a product.\n" +
        "• Categories: rename, reorder, hide or add them with 'Manage categories'."

@Composable
fun ProductsTab(
    viewModel: AdminCatalogViewModel,
    onEditProduct: (productId: String?) -> Unit,
    onManageCategories: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { viewModel.load() }

    Column(Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 4.dp, top = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Products", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            InfoButton(title = "Products — what to enter", text = PRODUCTS_INFO)
        }
        Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { onEditProduct(null) }) { Text("+ Add product") }
            OutlinedButton(onClick = onManageCategories) { Text("Manage categories") }
        }
        AdminMessageBanner(state.message, onShown = viewModel::clearMessage)
        state.loadError?.let { AdminMessageBanner(AdminMessage(it, isError = true), onShown = {}) }

        if (state.isLoading && state.products.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            return@Column
        }
        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            items(state.products, key = { it.id }) { product ->
                Card(
                    onClick = { onEditProduct(product.id) },
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                ) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        ProductImage(
                            imageUrl = product.imageUrl,
                            fallbackIcon = Icons.Outlined.Storefront,
                            contentDescription = null,
                            modifier = Modifier
                                .size(56.dp)
                                .clip(RoundedCornerShape(10.dp)),
                        )
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(product.name, style = MaterialTheme.typography.titleSmall)
                            Text(
                                "${state.categoryName(product.categoryId)} · ${Money.formatRupees(product.pricePaise)}" +
                                    " · ${product.stockQuantity} in stock",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            if (!product.isActive) {
                                Text("Hidden from customers", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
                            }
                        }
                        Text("Edit", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.tertiary)
                    }
                }
            }
        }
    }
}

/** Add or edit one product, including its photo. [productId] null means a new product. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProductEditScreen(
    productId: String?,
    viewModel: AdminCatalogViewModel,
    onDone: () -> Unit,
    onBack: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val existing = state.products.firstOrNull { it.id == productId }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var name by rememberSaveable(existing?.id) { mutableStateOf(existing?.name.orEmpty()) }
    var description by rememberSaveable(existing?.id) { mutableStateOf(existing?.description.orEmpty()) }
    var priceText by rememberSaveable(existing?.id) {
        mutableStateOf(existing?.pricePaise?.let(AdminInput::rupeesForInput).orEmpty())
    }
    var categoryId by rememberSaveable(existing?.id) {
        mutableStateOf(existing?.categoryId ?: state.categories.firstOrNull()?.id.orEmpty())
    }
    var isActive by rememberSaveable(existing?.id) { mutableStateOf(existing?.isActive ?: true) }
    var photo by remember { mutableStateOf<ByteArray?>(null) }
    var photoError by remember { mutableStateOf<String?>(null) }
    var categoryMenuOpen by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        if (state.products.isEmpty()) viewModel.load()
        viewModel.clearSavedProduct()
    }
    LaunchedEffect(state.savedProductId) {
        if (state.savedProductId != null) {
            viewModel.clearSavedProduct()
            onDone()
        }
    }

    val pickPhoto = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            scope.launch {
                photoError = null
                photo = runCatching { withContext(Dispatchers.IO) { compressPhoto(context, uri) } }
                    .onFailure { photoError = "Could not read that photo. Please try another." }
                    .getOrNull()
            }
        }
    }

    val pricePaise = AdminInput.parseRupees(priceText)
    val formError = when {
        name.isBlank() -> "Enter a product name"
        categoryId.isBlank() -> "Choose a category"
        pricePaise == null -> "Enter a valid price in rupees, e.g. 45 or 45.50"
        else -> null
    }

    Column(
        Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.navigationBars)
            .imePadding(),
    ) {
        BackTopBar(title = if (productId == null) "Add product" else "Edit product", onBack = onBack)
        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            // Photo
            Box(
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(4f / 3f)
                    .clip(RoundedCornerShape(16.dp)),
            ) {
                val bytes = photo
                if (bytes != null) {
                    val bitmap = remember(bytes) { BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap() }
                    if (bitmap != null) {
                        Image(
                            bitmap = bitmap,
                            contentDescription = "New photo",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                } else {
                    ProductImage(
                        imageUrl = existing?.imageUrl,
                        fallbackIcon = Icons.Outlined.Storefront,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
            OutlinedButton(
                onClick = { pickPhoto.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                modifier = Modifier.fillMaxWidth(),
            ) { Text(if (existing?.imageUrl != null || photo != null) "Change photo" else "Add photo") }
            photoError?.let { MessageCard(message = it, isError = true) }

            OutlinedTextField(
                value = name,
                onValueChange = { name = it.take(100) },
                label = { Text("Product name") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            ExposedDropdownMenuBox(expanded = categoryMenuOpen, onExpandedChange = { categoryMenuOpen = it }) {
                OutlinedTextField(
                    value = state.categories.firstOrNull { it.id == categoryId }?.name ?: "Choose category",
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Category") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = categoryMenuOpen) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .menuAnchor(MenuAnchorType.PrimaryNotEditable),
                )
                ExposedDropdownMenu(
                    expanded = categoryMenuOpen,
                    onDismissRequest = { categoryMenuOpen = false },
                ) {
                    state.categories.forEach { category ->
                        DropdownMenuItem(
                            text = { Text(category.name + if (!category.isActive) " (hidden)" else "") },
                            onClick = {
                                categoryId = category.id
                                categoryMenuOpen = false
                            },
                        )
                    }
                }
            }
            OutlinedTextField(
                value = priceText,
                onValueChange = { priceText = it.take(10) },
                label = { Text("Price per item (₹)") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                isError = priceText.isNotBlank() && pricePaise == null,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = description,
                onValueChange = { description = it.take(1000) },
                label = { Text("Description (optional)") },
                minLines = 2,
                modifier = Modifier.fillMaxWidth(),
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Show in app", style = MaterialTheme.typography.titleSmall)
                    Text(
                        "Off hides it from customers but keeps its sales history.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(checked = isActive, onCheckedChange = { isActive = it })
            }
            state.message?.takeIf { it.isError }?.let { MessageCard(message = it.text, isError = true) }
            SubmitButton(
                text = "Save product",
                isSubmitting = state.isSaving,
                enabled = formError == null,
                onClick = {
                    viewModel.saveProduct(
                        ProductDraft(
                            id = existing?.id,
                            categoryId = categoryId,
                            name = name,
                            description = description,
                            pricePaise = pricePaise ?: 0,
                            isActive = isActive,
                        ),
                        photoJpeg = photo,
                    )
                },
            )
            formError?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

/** Shrinks a gallery photo to at most 1280 px and JPEG quality 85 (well under the 5 MB limit). */
private fun compressPhoto(context: Context, uri: Uri, maxSide: Int = 1280): ByteArray {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
    var sample = 1
    while (bounds.outWidth / (sample * 2) >= maxSide || bounds.outHeight / (sample * 2) >= maxSide) sample *= 2
    val decoded = context.contentResolver.openInputStream(uri)?.use {
        BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
    } ?: error("Unreadable image")
    val scale = minOf(1f, maxSide.toFloat() / maxOf(decoded.width, decoded.height))
    val scaled = if (scale < 1f) {
        Bitmap.createScaledBitmap(decoded, (decoded.width * scale).toInt(), (decoded.height * scale).toInt(), true)
    } else {
        decoded
    }
    return ByteArrayOutputStream().use { out ->
        scaled.compress(Bitmap.CompressFormat.JPEG, 85, out)
        out.toByteArray()
    }
}

/** List of categories with add / edit. */
@Composable
fun CategoriesScreen(viewModel: AdminCatalogViewModel, onBack: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var editing by remember { mutableStateOf<AdminCategory?>(null) }
    var adding by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { if (state.categories.isEmpty()) viewModel.load() }

    Column(
        Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.navigationBars),
    ) {
        BackTopBar(title = "Categories", onBack = onBack)
        Button(
            onClick = { adding = true },
            modifier = Modifier.padding(horizontal = 16.dp),
        ) { Text("+ Add category") }
        AdminMessageBanner(state.message, onShown = viewModel::clearMessage)
        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(state.categories, key = { it.id }) { category ->
                Card(
                    onClick = { editing = category },
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                ) {
                    Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(category.name, style = MaterialTheme.typography.titleSmall)
                            Text(
                                "Order ${category.sortOrder}" + (if (category.tagline.isNotBlank()) " · ${category.tagline}" else "") +
                                    if (!category.isActive) " · hidden" else "",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Text("Edit", color = MaterialTheme.colorScheme.tertiary)
                    }
                }
            }
        }
    }

    if (adding || editing != null) {
        val category = editing
        CategoryDialog(
            category = category,
            nextSortOrder = (state.categories.maxOfOrNull { it.sortOrder } ?: 0) + 10,
            isSaving = state.isSaving,
            onDismiss = {
                adding = false
                editing = null
            },
            onSave = { draft ->
                adding = false
                editing = null
                viewModel.saveCategory(draft)
            },
        )
    }
}

@Composable
private fun CategoryDialog(
    category: AdminCategory?,
    nextSortOrder: Int,
    isSaving: Boolean,
    onDismiss: () -> Unit,
    onSave: (CategoryDraft) -> Unit,
) {
    var name by remember { mutableStateOf(category?.name.orEmpty()) }
    var tagline by remember { mutableStateOf(category?.tagline.orEmpty()) }
    var orderText by remember { mutableStateOf((category?.sortOrder ?: nextSortOrder).toString()) }
    var isActive by remember { mutableStateOf(category?.isActive ?: true) }
    val sortOrder = orderText.trim().toIntOrNull()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (category == null) "Add category" else "Edit category") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = name, onValueChange = { name = it.take(60) }, label = { Text("Name") }, singleLine = true)
                OutlinedTextField(
                    value = tagline,
                    onValueChange = { tagline = it.take(120) },
                    label = { Text("Short description (optional)") },
                    singleLine = true,
                )
                OutlinedTextField(
                    value = orderText,
                    onValueChange = { orderText = it.take(5) },
                    label = { Text("Display order (smaller shows first)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Show in app", modifier = Modifier.weight(1f))
                    Switch(checked = isActive, onCheckedChange = { isActive = it })
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onSave(CategoryDraft(category?.id, name, tagline, sortOrder ?: 0, isActive)) },
                enabled = name.isNotBlank() && sortOrder != null && !isSaving,
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
