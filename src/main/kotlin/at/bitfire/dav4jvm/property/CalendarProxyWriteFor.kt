/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package at.bitfire.dav4jvm.property

import at.bitfire.dav4jvm.Property
import at.bitfire.dav4jvm.XmlUtils
import org.kobjects.ktxml.mini.MiniXmlPullParser

class CalendarProxyWriteFor: HrefListProperty() {

    companion object {
        @JvmField
        val NAME = Property.Name(XmlUtils.NS_CALENDARSERVER, "calendar-proxy-write-for")
    }


    object Factory: HrefListProperty.Factory() {

        override fun getName() = NAME

        override fun create(parser: MiniXmlPullParser) =
                create(parser, CalendarProxyWriteFor())

    }

}
