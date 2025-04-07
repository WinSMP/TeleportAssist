package org.winlogon.teleportassist

/** A collection of utility functions which may be useful in more than one place
  */
object Utilities {

    /** Detects if the server is running on Folia by attempting to access the RegionizedServer class
      * which is available to only Folia.
      *
      * @return
      *   True if the server is running on Folia
      */
    def detectFolia(): Boolean = {
        try {
            Class.forName("io.papermc.paper.threadedregions.RegionizedServer")
            true
        } catch {
            case _: ClassNotFoundException => false
        }
    }
}
