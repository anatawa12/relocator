package com.anatawa12.relocator.internal

import com.anatawa12.relocator.file.SingleFile
import com.anatawa12.relocator.internal.ClassContainer.Jar.Companion.META_INF_VERSIONS
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.*
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldNotContain
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.OutputStream
import java.nio.file.Files
import java.util.jar.JarFile
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class ClassContainerJarTest : DescribeSpec() {
    private fun makeJar(out: OutputStream, withManifest: Boolean) {
        ZipOutputStream(out).use { zipOut ->
            val writer = zipOut.writer()

            // root entries
            zipOut.putNextEntry(ZipEntry("root-only.txt"))
            writer.apply { write("root") }.flush()
            zipOut.putNextEntry(ZipEntry("9-root.txt"))
            writer.apply { write("root") }.flush()
            zipOut.putNextEntry(ZipEntry("9-10-root.txt"))
            writer.apply { write("root") }.flush()
            zipOut.putNextEntry(ZipEntry("8-root.txt"))
            writer.apply { write("root") }.flush()

            // release 9 entries for basic
            zipOut.putNextEntry(ZipEntry("$META_INF_VERSIONS/9/9-only.txt"))
            writer.apply { write("9") }.flush()
            zipOut.putNextEntry(ZipEntry("$META_INF_VERSIONS/9/9-root.txt"))
            writer.apply { write("9") }.flush()
            zipOut.putNextEntry(ZipEntry("$META_INF_VERSIONS/9/9-10-root.txt"))
            writer.apply { write("9") }.flush()
            zipOut.putNextEntry(ZipEntry("$META_INF_VERSIONS/9/9-10-only.txt"))
            writer.apply { write("9") }.flush()

            // release 10 entries for multiple release
            zipOut.putNextEntry(ZipEntry("$META_INF_VERSIONS/10/10-only.txt"))
            writer.apply { write("10") }.flush()
            zipOut.putNextEntry(ZipEntry("$META_INF_VERSIONS/10/9-10-root.txt"))
            writer.apply { write("10") }.flush()
            zipOut.putNextEntry(ZipEntry("$META_INF_VERSIONS/10/9-10-only.txt"))
            writer.apply { write("10") }.flush()

            // invalid version/release: 8
            zipOut.putNextEntry(ZipEntry("$META_INF_VERSIONS/8/8-only.txt"))
            writer.apply { write("8") }.flush()
            zipOut.putNextEntry(ZipEntry("$META_INF_VERSIONS/8/8-root.txt"))
            writer.apply { write("8") }.flush()

            // files on META-INF/versions
            zipOut.putNextEntry(ZipEntry("$META_INF_VERSIONS/9"))
            writer.apply { write("$META_INF_VERSIONS/9") }.flush()
            zipOut.putNextEntry(ZipEntry("$META_INF_VERSIONS/test.txt"))
            writer.apply { write("$META_INF_VERSIONS/test.txt") }.flush()

            // enable multi release if withManifest is true
            if (withManifest) {
                zipOut.putNextEntry(ZipEntry(JarFile.MANIFEST_NAME))
                writer.apply {
                    write("""
                    Manifest-Version: 1.0
                    Multi-Release: true

                """.trimIndent())
                }.flush()
            }
        }
    }

    init {
        describe("multi release support") {
            val temp = withContext(Dispatchers.IO) {
                val temp = Files.createTempFile("multi-release", ".jar")
                makeJar(Files.newOutputStream(temp), true)
                temp
            }
            val jar = ClassContainer.Jar(temp.toFile())

            it("version list") {
                jar.releases.toSet() shouldBe setOf(9, 10)
            }

            it("check path list") {
                // check multi-release resources
                jar.files shouldContain "root-only.txt"
                jar.files shouldContain "9-only.txt"
                jar.files shouldContain "10-only.txt"
                jar.files shouldContain "9-10-only.txt"

                jar.files shouldContain "9-root.txt"
                jar.files shouldContain "9-10-root.txt"
                jar.files shouldContain "8-root.txt"

                // check files in versions/8 are exists
                jar.files shouldContain "$META_INF_VERSIONS/8/8-only.txt"
                jar.files shouldContain "$META_INF_VERSIONS/8/8-root.txt"

                // check files on META-INF/versions
                jar.files shouldContain "$META_INF_VERSIONS/9"
                jar.files shouldContain "$META_INF_VERSIONS/test.txt"

                // check files in versions/(9|10) are not exists
                jar.files shouldNotContain "$META_INF_VERSIONS/9/9-only.txt"
                jar.files shouldNotContain "$META_INF_VERSIONS/9/9-root.txt"
                jar.files shouldNotContain "$META_INF_VERSIONS/9/9-10-root.txt"
                jar.files shouldNotContain "$META_INF_VERSIONS/9/9-10-only.txt"

                jar.files shouldNotContain "$META_INF_VERSIONS/10/10-only.txt"
                jar.files shouldNotContain "$META_INF_VERSIONS/10/9-10-root.txt"
                jar.files shouldNotContain "$META_INF_VERSIONS/10/9-10-only.txt"
            }

            it("check resolve") {
                // root entries
                jar.loadFiles("root-only.txt") should haveFile("root", 0)
                jar.loadFiles("9-root.txt") should haveFile("root", 0)
                jar.loadFiles("9-10-root.txt") should haveFile("root", 0)
                jar.loadFiles("8-root.txt") should haveFile("root", 0)

                // release 9 entries for basic
                jar.loadFiles("$META_INF_VERSIONS/9/9-only.txt") should haveFile("9", 0)
                jar.loadFiles("$META_INF_VERSIONS/9/9-root.txt") should haveFile("9", 0)
                jar.loadFiles("$META_INF_VERSIONS/9/9-10-root.txt") should haveFile("9", 0)
                jar.loadFiles("$META_INF_VERSIONS/9/9-10-only.txt") should haveFile("9", 0)
                jar.loadFiles("$META_INF_VERSIONS/9/9-only.txt").size shouldBe 1
                jar.loadFiles("$META_INF_VERSIONS/9/9-root.txt").size shouldBe 1
                jar.loadFiles("$META_INF_VERSIONS/9/9-10-root.txt").size shouldBe 1
                jar.loadFiles("$META_INF_VERSIONS/9/9-10-only.txt").size shouldBe 1
                jar.loadFiles("9-only.txt") should haveFile("9", 9)
                jar.loadFiles("9-root.txt") should haveFile("9", 9)
                jar.loadFiles("9-10-root.txt") should haveFile("9", 9)
                jar.loadFiles("9-10-only.txt") should haveFile("9", 9)

                // release 10 entries for multiple release
                jar.loadFiles("$META_INF_VERSIONS/10/10-only.txt") should haveFile("10", 0)
                jar.loadFiles("$META_INF_VERSIONS/10/9-10-root.txt") should haveFile("10", 0)
                jar.loadFiles("$META_INF_VERSIONS/10/9-10-only.txt") should haveFile("10", 0)
                jar.loadFiles("$META_INF_VERSIONS/10/10-only.txt").size shouldBe 1
                jar.loadFiles("$META_INF_VERSIONS/10/9-10-root.txt").size shouldBe 1
                jar.loadFiles("$META_INF_VERSIONS/10/9-10-only.txt").size shouldBe 1
                jar.loadFiles("10-only.txt") should haveFile("10", 10)
                jar.loadFiles("9-10-root.txt") should haveFile("10", 10)
                jar.loadFiles("9-10-only.txt") should haveFile("10", 10)

                // invalid version/release: 8
                jar.loadFiles("$META_INF_VERSIONS/8/8-only.txt") should haveFile("8", 0)
                jar.loadFiles("$META_INF_VERSIONS/8/8-root.txt") should haveFile("8", 0)
                jar.loadFiles("8-only.txt").shouldBeEmpty()
                jar.loadFiles("8-root.txt") shouldNot haveRelease(8)

                // files on META-INF/versions
                jar.loadFiles("$META_INF_VERSIONS/9") should haveFile("$META_INF_VERSIONS/9", 0)
                jar.loadFiles("$META_INF_VERSIONS/test.txt") should haveFile("$META_INF_VERSIONS/test.txt", 0)
            }

            finalizeSpec { withContext(Dispatchers.IO) { Files.delete(temp) } }
        }
        describe("non multi release support") {
            val temp = withContext(Dispatchers.IO) {
                val temp = Files.createTempFile("multi-release", ".jar")
                makeJar(Files.newOutputStream(temp), false)
                temp
            }
            val jar = ClassContainer.Jar(temp.toFile())

            it("version list") {
                jar.releases.toSet().shouldBeEmpty()
            }

            it("check path list") {
                // check multi-release resources
                jar.files shouldContain "root-only.txt"
                jar.files shouldNotContain "9-only.txt"
                jar.files shouldNotContain "10-only.txt"
                jar.files shouldNotContain "9-10-only.txt"

                jar.files shouldContain "9-root.txt"
                jar.files shouldContain "9-10-root.txt"
                jar.files shouldContain "8-root.txt"

                // check files in versions/8 are exists
                jar.files shouldContain "$META_INF_VERSIONS/8/8-only.txt"
                jar.files shouldContain "$META_INF_VERSIONS/8/8-root.txt"

                // check files on META-INF/versions
                jar.files shouldContain "$META_INF_VERSIONS/9"
                jar.files shouldContain "$META_INF_VERSIONS/test.txt"

                // check files in versions/(9|10) are not exists
                jar.files shouldContain "$META_INF_VERSIONS/9/9-only.txt"
                jar.files shouldContain "$META_INF_VERSIONS/9/9-root.txt"
                jar.files shouldContain "$META_INF_VERSIONS/9/9-10-root.txt"
                jar.files shouldContain "$META_INF_VERSIONS/9/9-10-only.txt"

                jar.files shouldContain "$META_INF_VERSIONS/10/10-only.txt"
                jar.files shouldContain "$META_INF_VERSIONS/10/9-10-root.txt"
                jar.files shouldContain "$META_INF_VERSIONS/10/9-10-only.txt"
            }

            it("check resolve") {
                // root entries
                jar.loadFiles("root-only.txt") should haveFile("root", 0)
                jar.loadFiles("9-root.txt") should haveFile("root", 0)
                jar.loadFiles("9-10-root.txt") should haveFile("root", 0)
                jar.loadFiles("8-root.txt") should haveFile("root", 0)

                // release 9 entries for basic
                jar.loadFiles("$META_INF_VERSIONS/9/9-only.txt") should haveFile("9", 0)
                jar.loadFiles("$META_INF_VERSIONS/9/9-root.txt") should haveFile("9", 0)
                jar.loadFiles("$META_INF_VERSIONS/9/9-10-root.txt") should haveFile("9", 0)
                jar.loadFiles("$META_INF_VERSIONS/9/9-10-only.txt") should haveFile("9", 0)
                jar.loadFiles("$META_INF_VERSIONS/9/9-only.txt").size shouldBe 1
                jar.loadFiles("$META_INF_VERSIONS/9/9-root.txt").size shouldBe 1
                jar.loadFiles("$META_INF_VERSIONS/9/9-10-root.txt").size shouldBe 1
                jar.loadFiles("$META_INF_VERSIONS/9/9-10-only.txt").size shouldBe 1
                jar.loadFiles("9-only.txt").shouldBeEmpty()
                jar.loadFiles("9-root.txt").size shouldBe 1
                jar.loadFiles("9-10-root.txt").size shouldBe 1
                jar.loadFiles("9-10-only.txt").shouldBeEmpty()

                // release 10 entries for multiple release
                jar.loadFiles("$META_INF_VERSIONS/10/10-only.txt") should haveFile("10", 0)
                jar.loadFiles("$META_INF_VERSIONS/10/9-10-root.txt") should haveFile("10", 0)
                jar.loadFiles("$META_INF_VERSIONS/10/9-10-only.txt") should haveFile("10", 0)
                jar.loadFiles("$META_INF_VERSIONS/10/10-only.txt").size shouldBe 1
                jar.loadFiles("$META_INF_VERSIONS/10/9-10-root.txt").size shouldBe 1
                jar.loadFiles("$META_INF_VERSIONS/10/9-10-only.txt").size shouldBe 1
                jar.loadFiles("10-only.txt").shouldBeEmpty()
                jar.loadFiles("9-10-root.txt").size shouldBe 1
                jar.loadFiles("9-10-only.txt").shouldBeEmpty()

                // invalid version/release: 8
                jar.loadFiles("$META_INF_VERSIONS/8/8-only.txt") should haveFile("8", 0)
                jar.loadFiles("$META_INF_VERSIONS/8/8-root.txt") should haveFile("8", 0)
                jar.loadFiles("8-only.txt").shouldBeEmpty()
                jar.loadFiles("8-root.txt") shouldNot haveRelease(8)

                // files on META-INF/versions
                jar.loadFiles("$META_INF_VERSIONS/9") should haveFile("$META_INF_VERSIONS/9", 0)
                jar.loadFiles("$META_INF_VERSIONS/test.txt") should haveFile("$META_INF_VERSIONS/test.txt", 0)
            }

            finalizeSpec { withContext(Dispatchers.IO) { Files.delete(temp) } }
        }
    }

    private fun haveFile(body: String, release: Int) = object : Matcher<Collection<SingleFile>> {
        override fun test(value: Collection<SingleFile>): MatcherResult =
            if (value.any { it.data.contentEquals(body.toByteArray()) && it.release == release }) {
                MatcherResult(true, { error("") },
                    { "Collection that have SingleFile for release $release with body '$body'is not expected." }
                )
            } else {
                val contentEq = value.firstOrNull { it.data.contentEquals(body.toByteArray()) }
                if (contentEq != null) {
                    MatcherResult(
                        false,
                        {
                            "Expected collection that have SingleFile for release $release with body '$body'" +
                                    " but not found for $release"
                        },
                        { error("") }
                    )
                } else {
                    MatcherResult(
                        false,
                        { "Expected collection that have SingleFile for release $release with body '$body'." },
                        { error("") }
                    )
                }
            }
    }

    private fun haveRelease(@Suppress("SameParameterValue") release: Int) = object : Matcher<Collection<SingleFile>> {
        override fun test(value: Collection<SingleFile>) = MatcherResult(
            value.any { it.release == release },
            { "Expected collection that have SingleFile for release $release." },
            { "Collection that have SingleFile for release $release is not expected." }
        )
    }
}
