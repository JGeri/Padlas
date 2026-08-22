package hu.padlas.core.database.entity

/** Lifecycle of a single asset. GHOST = on Drive, locally only a thumbnail remains. */
enum class AssetState { LOCAL, UPLOADING, ARCHIVED, GHOST, TRASHED, PROTECTED }

/** Decides how much friction deleting this asset costs. See spec 1.5 / INV-1. */
enum class ValueClass { IRREPLACEABLE, REPLACEABLE, EPHEMERAL }

/** Screenshot triage result (M7). SECRET overrides everything (INV-1c). */
enum class ShotKind { RECEIPT, TICKET, SECRET, PLACE, CHAT, OTHER }

enum class DupKind {
    /** Byte identical, SHA-256 match. Nothing to decide. */
    EXACT,

    /** dHash distance <= 6 and shot within 10s of each other: a burst. */
    BURST,

    /** dHash distance 7..12: similar composition, one human decision per group. */
    SIMILAR,
}

enum class JobState { PENDING, RUNNING, SUCCEEDED, FAILED, CANCELLED }

enum class RuleAction { ARCHIVE, TRASH, PROTECT, TRANSCODE }

enum class AuditAction {
    INDEXED,
    CLASSIFIED,
    HASHED,
    OCR,
    ARCHIVED,
    GHOSTED,
    RESTORED,
    TRASHED,
    DELETED,
    UNTRASHED,
    TRANSCODED,
    PROTECTED,
    UNPROTECTED,
    RULE_RUN,
    CARD_WRITTEN,
    RECONCILED,
}

/** Where the value class came from. MANUAL always wins and is never overwritten. */
object ClassifiedBy {
    const val EXIF = "exif"
    const val RESOLUTION = "resolution"
    const val PATH = "path"
    const val MIME = "mime"
    const val MANUAL = "manual"
    const val TRIAGE = "triage"
}
