package hu.padlas.feature.rules

import com.google.common.truth.Truth.assertThat
import hu.padlas.core.database.entity.ShotKind
import hu.padlas.core.database.entity.ValueClass
import org.junit.Test

class PredicateCompilerTest {

    private val now = 1_700_000_000_000L
    private val dayMs = 24L * 60 * 60 * 1000

    @Test
    fun `path predicate binds a wildcard argument instead of inlining it`() {
        val compiled = PredicateCompiler.compile(Predicate.PathContains("WhatsApp"), now)

        assertThat(compiled.where).isEqualTo("relativePath LIKE ?")
        assertThat(compiled.args).containsExactly("%WhatsApp%")
    }

    @Test
    fun `a quote in a folder name never reaches the sql text`() {
        val compiled = PredicateCompiler.compile(Predicate.PathContains("O'Brien'; DROP TABLE asset--"), now)

        assertThat(compiled.where).doesNotContain("DROP")
        assertThat(compiled.args).hasSize(1)
    }

    @Test
    fun `older than resolves the cutoff against the supplied clock`() {
        val compiled = PredicateCompiler.compile(Predicate.OlderThan(90), now)

        assertThat(compiled.where).isEqualTo("dateTaken < ?")
        assertThat(compiled.args).containsExactly(now - 90 * dayMs)
    }

    @Test
    fun `enums are bound by name`() {
        val classCompiled = PredicateCompiler.compile(Predicate.ValueClassIs(ValueClass.EPHEMERAL), now)
        val kindCompiled = PredicateCompiler.compile(Predicate.ShotKindIs(ShotKind.RECEIPT), now)

        assertThat(classCompiled.args).containsExactly("EPHEMERAL")
        assertThat(kindCompiled.args).containsExactly("RECEIPT")
    }

    @Test
    fun `nested and or not keeps argument order`() {
        val predicate = Predicate.And(
            listOf(
                Predicate.PathContains("WhatsApp"),
                Predicate.Or(
                    listOf(
                        Predicate.OlderThan(30),
                        Predicate.Not(Predicate.LargerThan(1_048_576)),
                    ),
                ),
            ),
        )

        val compiled = PredicateCompiler.compile(predicate, now)

        assertThat(compiled.where)
            .isEqualTo("(relativePath LIKE ? AND (dateTaken < ? OR NOT (sizeBytes > ?)))")
        assertThat(compiled.args)
            .containsExactly("%WhatsApp%", now - 30 * dayMs, 1_048_576L)
            .inOrder()
    }

    @Test
    fun `the statement always carries the automation base clause`() {
        val statement = PredicateCompiler.selectAssets(Predicate.OlderThan(1), now, limit = 5)

        assertThat(statement.where).contains(PredicateCompiler.BASE_CLAUSE)
        assertThat(statement.where).endsWith("LIMIT 5")
    }

    @Test
    fun `an empty and matches everything rather than crashing`() {
        val compiled = PredicateCompiler.compile(Predicate.And(emptyList()), now)

        assertThat(compiled.where).isEqualTo("1")
        assertThat(compiled.args).isEmpty()
    }

    @Test
    fun `predicates survive the json round trip`() {
        val predicate = Predicate.And(
            listOf(
                Predicate.MimeIs("video/"),
                Predicate.Not(Predicate.BucketIs(42)),
                Predicate.ShotKindIs(ShotKind.SECRET),
            ),
        )

        val decoded = PredicateJson.decode(PredicateJson.encode(predicate))

        assertThat(decoded).isEqualTo(predicate)
    }
}
