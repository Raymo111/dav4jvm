/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package at.bitfire.dav4jvm.property

import at.bitfire.dav4jvm.Property
import at.bitfire.dav4jvm.PropertyFactory
import at.bitfire.dav4jvm.XmlUtils
import org.kobjects.ktxml.api.EventType
import org.kobjects.ktxml.mini.MiniXmlPullParser

class SupportedReportSet: Property {

    companion object {

        @JvmField
        val NAME = Property.Name(XmlUtils.NS_WEBDAV, "supported-report-set")

        val SUPPORTED_REPORT = Property.Name(XmlUtils.NS_WEBDAV, "supported-report")
        val REPORT = Property.Name(XmlUtils.NS_WEBDAV, "report")

        const val SYNC_COLLECTION = "DAV:sync-collection"    // collection synchronization (RFC 6578)

    }

    val reports = mutableSetOf<String>()

    override fun toString() = "[${reports.joinToString(", ")}]"


    object Factory: PropertyFactory {

        override fun getName() = NAME

        override fun create(parser: MiniXmlPullParser): SupportedReportSet? {
            /* <!ELEMENT supported-report-set (supported-report*)>
               <!ELEMENT supported-report report>
               <!ELEMENT report ANY>
            */

            val supported = SupportedReportSet()
            XmlUtils.processTag(parser, SUPPORTED_REPORT) {
                XmlUtils.processTag(parser, REPORT) {
                    parser.next()
                    if (parser.eventType == EventType.TEXT)
                        supported.reports += parser.text
                    else if (parser.eventType == EventType.START_TAG)
                        supported.reports += "${parser.namespace}${parser.name}"
                }
            }
            return supported
        }

    }

}
