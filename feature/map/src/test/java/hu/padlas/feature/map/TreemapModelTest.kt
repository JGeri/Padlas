package hu.padlas.feature.map

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class TreemapModelTest {

    private fun item(name: String, bytes: Long, isDirectory: Boolean = false) = StorageItem(
        path = "$ROOT/$name",
        name = name,
        parentPath = ROOT,
        bytes = bytes,
        modified = 0L,
        category = FileTypes.categoryOf(name),
        isDirectory = isDirectory,
        fileCount = 0,
    )

    @Test
    fun `a short list is drawn item by item`() {
        val entries = listOf(item("a.mp4", 500), item("b.jpg", 300), item("c.pdf", 200))

        val plan = TreemapPlanner.plan(entries)

        assertThat(plan.items.map { it.name }).containsExactly("a.mp4", "b.jpg", "c.pdf").inOrder()
        assertThat(plan.restBytes).isEqualTo(0)
    }

    @Test
    fun `the long tail folds into one tile and the cap holds`() {
        val entries = (1..1_000).map { item("f$it.bin", it.toLong()) }

        val plan = TreemapPlanner.plan(entries, maxTiles = 250)

        assertThat(plan.items.size + 1).isAtMost(250)
        assertThat(plan.items.first().name).isEqualTo("f1000.bin")
        assertThat(plan.restCount).isEqualTo(1_000 - plan.items.size)
        assertThat(plan.items.sumOf { it.bytes } + plan.restBytes).isEqualTo(entries.sumOf { it.bytes })
    }

    @Test
    fun `slivers below the minimum share are folded even under the cap`() {
        val entries = listOf(item("huge.mp4", 1_000_000)) + (1..5).map { item("tiny$it.txt", 1) }

        val plan = TreemapPlanner.plan(entries, minShare = 0.001)

        assertThat(plan.items.map { it.name }).containsExactly("huge.mp4")
        assertThat(plan.restCount).isEqualTo(5)
        assertThat(plan.restBytes).isEqualTo(5)
    }

    @Test
    fun `a single leftover is shown by name instead of folded`() {
        val entries = listOf(item("huge.mp4", 1_000_000), item("tiny.txt", 1))

        val plan = TreemapPlanner.plan(entries, minShare = 0.001)

        assertThat(plan.items.map { it.name }).containsExactly("huge.mp4", "tiny.txt").inOrder()
        assertThat(plan.restBytes).isEqualTo(0)
    }

    @Test
    fun `empty entries are counted but never drawn`() {
        val entries = listOf(item("a.mp4", 100), item("zero.txt", 0), item("empty", 0, isDirectory = true))

        val plan = TreemapPlanner.plan(entries)

        assertThat(plan.items.map { it.name }).containsExactly("a.mp4")
        assertThat(plan.restBytes).isEqualTo(0)
        assertThat(TreemapPlanner.plan(listOf(item("zero.txt", 0))).items).isEmpty()
    }

    @Test
    fun `the layout nests the children of large folders`() {
        val root = tree {
            dir("DCIM") {
                file("a.jpg", 600)
                file("b.mp4", 400)
            }
            file("small.pdf", 10)
        }
        val listing = DirectoryListing.build(root, ROOT, version = 1)
        val metrics = TreemapMetrics(gap = 2f, header = 20f, padding = 3f, nestedGap = 1f, nestMinWidth = 80f, nestMinHeight = 70f)

        val layout = layoutTreemap(listing, listing.plan, 1000f, 1000f, metrics)

        val dcim = layout.tile("$ROOT/DCIM") ?: error("missing")
        assertThat(dcim.inner).isNotNull()
        assertThat(dcim.nested).hasSize(2)
        assertThat(dcim.category).isEqualTo(FileCategory.IMAGES)
        val inner = dcim.inner ?: error("missing")
        dcim.nested.forEach { child ->
            assertThat(child.rect.left).isAtLeast(inner.left)
            assertThat(child.rect.right).isAtMost(inner.right)
            assertThat(child.rect.top).isAtLeast(inner.top)
            assertThat(child.rect.bottom).isAtMost(inner.bottom)
        }
        assertThat(layout.tileLeadingTo("$ROOT/DCIM/sub")?.key).isEqualTo("$ROOT/DCIM")
        assertThat(layout.hit(dcim.rect.centerX, dcim.rect.centerY)?.key).isEqualTo("$ROOT/DCIM")
    }

    @Test
    fun `previews keep the biggest children and fold the rest`() {
        val root = tree { dir("Camera") { repeat(30) { file("IMG_$it.jpg", (it + 1).toLong()) } } }
        val camera = root.child("Camera") ?: error("missing")

        val preview = DirectoryListing.previewOf(camera, limit = 24)

        assertThat(preview).hasSize(25)
        assertThat(preview.first().bytes).isEqualTo(30)
        assertThat(preview.last().isRest).isTrue()
        assertThat(preview.last().bytes).isEqualTo((1..6).sum().toLong())
        assertThat(preview.sumOf { it.bytes }).isEqualTo(camera.bytes)
    }

    @Test
    fun `the listing sorts by size and builds the breadcrumb`() {
        val root = tree {
            dir("DCIM") {
                dir("Camera") { file("x.jpg", 10) }
                file("big.mp4", 100)
            }
        }

        val listing = DirectoryListing.build(root, "$ROOT/DCIM", version = 3)

        assertThat(listing.entries.map { it.name }).containsExactly("big.mp4", "Camera").inOrder()
        assertThat(listing.crumbs.map { it.label }).containsExactly(DirectoryListing.ROOT_LABEL, "DCIM").inOrder()
        assertThat(listing.parentPath).isEqualTo(ROOT)
        assertThat(listing.isRoot).isFalse()
        assertThat(DirectoryListing.build(root, "$ROOT/DCIM/Gone", 3).path).isEqualTo("$ROOT/DCIM")
    }

    @Test
    fun `rectangles map between frames`() {
        val from = MapRect(0f, 0f, 100f, 100f)
        val to = MapRect(10f, 20f, 60f, 120f)

        val mapped = MapRect(50f, 50f, 100f, 100f).mapBetween(from, to)

        assertThat(mapped).isEqualTo(MapRect(35f, 70f, 60f, 120f))
    }
}
