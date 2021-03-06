package org.jetbrains.ktor.tests.html

import kotlinx.html.*
import org.jetbrains.ktor.application.*
import org.jetbrains.ktor.html.*
import org.jetbrains.ktor.http.*
import org.jetbrains.ktor.routing.*
import org.jetbrains.ktor.testing.*
import org.jetbrains.ktor.tests.*
import org.junit.*
import kotlin.test.*

class MenuTemplate : Template<FlowContent> {
    val item = PlaceholderList<FlowContent, FlowContent>()
    override fun FlowContent.apply() {
        if (!item.isEmpty()) {
            ul {
                each(item) {
                    li {
                        if (it.first) b {
                            insert(it)
                        } else {
                            insert(it)
                        }
                    }
                }
            }
        }
    }
}

class MainTemplate : Template<HTML> {
    val content = Placeholder<BODY>()
    val menu = TemplatePlaceholder<MenuTemplate>()
    override fun HTML.apply() {
        head {
            title { +"Template" }
        }
        body {
            h1 {
                insert(content)
            }
            insert(MenuTemplate(), menu)
        }
    }
}

class MulticolumnTemplate(val main: MainTemplate) : Template<HTML> {
    val column1 = Placeholder<FlowContent>()
    val column2 = Placeholder<FlowContent>()
    override fun HTML.apply() {
        insert(main) {
            menu {
                item { +"One" }
                item { +"Two" }
            }
            content {
                div("column") {
                    insert(column1)
                }
                div("column") {
                    insert(column2)
                }
            }
        }
    }
}

class HtmlTemplateTest {
    @Test
    fun testTemplate() = withTestApplication {
        application.routing {
            get("/") {
                val name = call.parameters["name"]

                call.respondHtmlTemplate(MulticolumnTemplate(MainTemplate())) {
                    column1 {
                        +"Hello, $name"
                    }
                    column2 {
                        +"col2"
                    }
                }
            }
        }

        handleRequest(HttpMethod.Get, "/?name=John").response.let { response ->
            assertNotNull(response.content)
            val lines = response.content!!
            assertEquals("""<!DOCTYPE html>
<html>
  <head>
    <title>Template</title>
  </head>
  <body>
    <h1>
      <div class="column">Hello, John</div>
      <div class="column">col2</div>
    </h1>
    <ul>
      <li><b>One</b></li>
      <li>Two</li>
    </ul>
  </body>
</html>
""", lines)
            val contentTypeText = assertNotNull(response.headers[HttpHeaders.ContentType])
            assertEquals(ContentType.Text.Html.withCharset(Charsets.UTF_8), ContentType.Companion.parse(contentTypeText))
        }
    }
}