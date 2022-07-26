package util.bundle

import java.util.{Locale, ResourceBundle}

sealed trait ResourceBundleUtil {
  def getBundle(lang: String): ResourceBundle
}

object ResourceBundleUtil {

  //TODO add purity
  def of(filepath: String, languages: List[String]): ResourceBundleUtil = {
    val availableLocales = languages.map(language => new Locale(language))
    val bundles          = availableLocales.map(locale => ResourceBundle.getBundle(filepath, locale))
    val bundlesM         = Map.from(languages.zip(bundles))
    new ResourceBundleUtil {
      override def getBundle(lang: String): ResourceBundle = bundlesM.getOrElse(lang, bundlesM("en"))
    }
  }
}
