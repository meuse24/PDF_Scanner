package info.meuse24.pdf_scanner.data.mapper

import info.meuse24.pdf_scanner.data.local.FolderEntity
import info.meuse24.pdf_scanner.domain.model.Folder

fun FolderEntity.toDomain(): Folder = Folder(
    id = id,
    name = name,
    colorArgb = colorArgb,
    createdAt = createdAt
)

fun Folder.toEntity(): FolderEntity = FolderEntity(
    id = id,
    name = name,
    colorArgb = colorArgb,
    createdAt = createdAt
)
