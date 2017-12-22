package org.koraseg.upstream

import java.util.concurrent.ThreadLocalRandom

import org.koraseg.botregistry.datamodel.Site


trait Chooser[T] {
  def choose: T
}


object RandomSiteChooser {
  lazy val sites: IndexedSeq[Site] = Vector(
    "www.google.com",
    "www.yahoo.com",
    "www.reddit.com",
    "www.nhl.com",
    "www.facebook.com",
    "www.stackoverflow.com",
    "www.twitter.com",
    "www.coursera.org",
    "www.github.com",
    "www.pornhub.com"
  )
}

trait RandomSiteChooser extends Chooser[Site] {
  import RandomSiteChooser._

  override def choose(): Site = {
    val rnd = ThreadLocalRandom.current()
    sites(rnd.nextInt(sites.length))
  }
}


