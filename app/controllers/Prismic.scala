package controllers

import scala.concurrent.duration._

import io.prismic.Fragment.DocumentLink
import io.prismic.{ Api => PrismicApi, _ }
import lila.app._
import lila.memo.AsyncCache

object Prismic {

  private val logger = (level: Symbol, message: String) => level match {
    case 'DEBUG => play.api.Logger("prismic") debug message
    case 'ERROR => play.api.Logger("prismic") error message
    case _      => play.api.Logger("prismic") info message
  }

  private val fetchPrismicApi = AsyncCache.single[PrismicApi](
    f = PrismicApi.get(Env.api.PrismicApiUrl, logger = logger),
    timeToLive = 1 minute)

  def prismicApi = fetchPrismicApi(true)

  implicit def makeLinkResolver(prismicApi: PrismicApi, ref: Option[String] = None) =
    DocumentLinkResolver(prismicApi) {
      case (DocumentLink(id, _, _, slug, false), _) => routes.Blog.show(id, slug, ref).url
      case _                                        => routes.Lobby.home.url
    }

  def getDocument(id: String): Fu[Option[Document]] = prismicApi flatMap { api =>
    api.forms("everything")
      .query(s"""[[:d = at(document.id, "$id")]]""")
      .ref(api.master.ref)
      .submit() map {
        _.results.headOption
      }
  }

  def getBookmark(name: String) = fetchPrismicApi(true) flatMap { api =>
    api.bookmarks.get(name) ?? getDocument map2 { (doc: io.prismic.Document) =>
      doc -> makeLinkResolver(api)
    }
  } recover {
    case e: Exception =>
      play.api.Logger("prismic").error(s"bookmark:$name $e")
      none
  }

  def getVariant(variant: chess.variant.Variant) = prismicApi flatMap { api =>
    api.forms("variant")
      .query(s"""[[:d = at(my.variant.key, "${variant.key}")]]""")
      .ref("Vqc_ySEAADcAVYS3")
      .submit() map {
        _.results.headOption map (_ -> makeLinkResolver(api))
      }
  } recover {
    case e: Exception =>
      play.api.Logger("prismic").error(s"variant:${variant.key} $e")
      none
  }
}
