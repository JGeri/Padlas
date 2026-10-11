package hu.padlas.feature.map

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.InsertDriveFile
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.FolderZip
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.InstallMobile
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import hu.padlas.core.design.theme.CategoryColor

internal val FileCategory.color: Color
    get() = when (this) {
        FileCategory.IMAGES -> CategoryColor.Photos
        FileCategory.VIDEOS -> CategoryColor.Videos
        FileCategory.AUDIO -> CategoryColor.Audio
        FileCategory.DOCUMENTS -> CategoryColor.Documents
        FileCategory.ARCHIVES -> CategoryColor.Archives
        FileCategory.INSTALLERS -> CategoryColor.Cloud
        FileCategory.OTHER -> CategoryColor.Other
    }

internal val FileCategory.icon: ImageVector
    get() = when (this) {
        FileCategory.IMAGES -> Icons.Rounded.Image
        FileCategory.VIDEOS -> Icons.Rounded.Movie
        FileCategory.AUDIO -> Icons.Rounded.MusicNote
        FileCategory.DOCUMENTS -> Icons.Rounded.Description
        FileCategory.ARCHIVES -> Icons.Rounded.FolderZip
        FileCategory.INSTALLERS -> Icons.Rounded.InstallMobile
        FileCategory.OTHER -> Icons.AutoMirrored.Rounded.InsertDriveFile
    }

internal val StorageItem.icon: ImageVector
    get() = if (isDirectory) Icons.Rounded.Folder else category.icon

internal val SummarySlice.color: Color
    get() = when (kind) {
        SliceKind.FILES -> category?.color ?: CategoryColor.Other
        SliceKind.APPS -> CategoryColor.Apps
        SliceKind.SYSTEM -> CategoryColor.System
    }

internal val SummarySlice.icon: ImageVector
    get() = when (kind) {
        SliceKind.FILES -> category?.icon ?: Icons.AutoMirrored.Rounded.InsertDriveFile
        SliceKind.APPS -> Icons.Rounded.Apps
        SliceKind.SYSTEM -> Icons.Rounded.Memory
    }

/** The three parts of an app's footprint, in the order the settings screen lists them. */
internal object AppPartColor {
    val Program = CategoryColor.Apps
    val Data = CategoryColor.Cloud
    val Cache = CategoryColor.Audio
}
