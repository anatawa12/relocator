package com.anatawa12.relocator.internal

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe

class SMAPParserTest : DescribeSpec({

    describe("readSMAP") {
        it("simple with embed") {
            val smap = """
                SMAP
                Hi.java
                Java
                *O Bar
                SMAP
                Hi.bar
                Java
                *S Foo
                *F
                1 Hi.foo
                *L
                1#1,5:1,2
                *E
                SMAP
                Incl.bar
                Java
                *S Foo
                *F
                1 Incl.foo
                *L
                1#1,2:1,2
                *E
                *C Bar
                *S Bar
                *F
                1 Hi.bar
                2 Incl.bar
                *L
                1#1:1
                1#2,4:2
                3#1,8:6
                *E
                
            """.trimIndent()

            val parsed = SMAPParser(smap).readSMAP()

            parsed shouldBe SMAP("Hi.java", "Java",
                EmbedSMAP("Bar",
                    SMAP("Hi.bar", "Java",
                        SMAPStratum("Foo",
                            SMAPFileSection(SMAPFileInfo(1, "Hi.foo")),
                            SMAPLineSection(SMAPLineInfo(1, 1, 5, 1, 2)),
                        ),
                    ),
                    SMAP("Incl.bar", "Java",
                        SMAPStratum("Foo",
                            SMAPFileSection(SMAPFileInfo(1, "Incl.foo")),
                            SMAPLineSection(SMAPLineInfo(1, 1, 2, 1, 2)),
                        ),
                    ),
                ),
                SMAPStratum("Bar",
                    SMAPFileSection(
                        SMAPFileInfo(1, "Hi.bar"),
                        SMAPFileInfo(2, "Incl.bar"),
                    ),
                    SMAPLineSection(
                        SMAPLineInfo(1, 1, -1, 1, -1),
                        SMAPLineInfo(1, 2, 4, 2, -1),
                        SMAPLineInfo(3, 1, 8, 6, -1),
                    ),
                ),
            )

            buildString { parsed.appendTo(this) } shouldBe smap
        }
        it("simple resolved") {
            val smap = """
                SMAP
                Hi.java
                Java
                *S Foo
                *F
                1 Hi.foo
                2 Incl.foo
                *L
                1#1,1:1,1
                2#1,4:6,2
                1#2,2:2,2
                *S Bar
                *F
                1 Hi.bar
                2 Incl.bar
                *L
                1#1:1
                1#2,4:2
                3#1,8:6
                *E
                
            """.trimIndent()

            val parsed = SMAPParser(smap).readSMAP()

            parsed shouldBe SMAP("Hi.java", "Java",
                SMAPStratum("Foo",
                    SMAPFileSection(
                        SMAPFileInfo(1, "Hi.foo"),
                        SMAPFileInfo(2, "Incl.foo"),
                    ),
                    SMAPLineSection(
                        SMAPLineInfo(1, 1, 1, 1, 1),
                        SMAPLineInfo(2, 1, 4, 6, 2),
                        SMAPLineInfo(1, 2, 2, 2, 2),
                    ),
                ),
                SMAPStratum("Bar",
                    SMAPFileSection(
                        SMAPFileInfo(1, "Hi.bar"),
                        SMAPFileInfo(2, "Incl.bar"),
                    ),
                    SMAPLineSection(
                        SMAPLineInfo(1, 1, -1, 1, -1),
                        SMAPLineInfo(1, 2, 4, 2, -1),
                        SMAPLineInfo(3, 1, 8, 6, -1),
                    ),
                ),
            )

            buildString { parsed.appendTo(this) } shouldBe smap
        }
    }
})
