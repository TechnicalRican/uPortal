/**
 * Copyright (c) 2000 The JA-SIG Collaborative.  All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 *
 * 1. Redistributions of source code must retain the above copyright
 *    notice, this list of conditions and the following disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright
 *    notice, this list of conditions and the following disclaimer in
 *    the documentation and/or other materials provided with the
 *    distribution.
 *
 * 3. Redistributions of any form whatsoever must retain the following
 *    acknowledgment:
 *    "This product includes software developed by the JA-SIG Collaborative
 *    (http://www.jasig.org/)."
 *
 * THIS SOFTWARE IS PROVIDED BY THE JA-SIG COLLABORATIVE "AS IS" AND ANY
 * EXPRESSED OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR
 * PURPOSE ARE DISCLAIMED.  IN NO EVENT SHALL THE JA-SIG COLLABORATIVE OR
 * ITS CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 * SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT
 * NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION)
 * HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT,
 * STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED
 * OF THE POSSIBILITY OF SUCH DAMAGE.
 *
 */

package org.jasig.portal.channels;

import org.jasig.portal.*;
import org.jasig.portal.utils.*;
import org.w3c.dom.*;
import org.apache.xalan.xslt.*;
import  org.apache.xerces.dom.*;
import org.xml.sax.DocumentHandler;
import java.io.*;
import java.util.*;

/**
 * Provides methods associated with subscribing to a channel.
 * This includes preview, listing all available channels
 * and placement on a users page.
 * @author John Laker
 * @version $Revision$
 */
public class CPublisher implements IPrivilegedChannel
{
  private boolean DEBUG = false;

  private ChannelStaticData staticData = null;
  private ChannelRuntimeData runtimeData = null;
  private StylesheetSet set = null;
  private ChannelRegistryImpl chanReg = null;

  private static final String fs = File.separator;
  private static final String portalBaseDir = GenericPortalBean.getPortalBaseDir ();
  String stylesheetDir = portalBaseDir + fs + "webpages" + fs + "stylesheets" + fs + "org" + fs + "jasig" + fs + "portal" + fs + "channels" + fs + "CPublisher";

  // channel modes
  private static final int NONE     = 0;
  private static final int CHOOSE   = 1;
  private static final int PUBLISH  = 2;
  private static final int PUBCATS  = 3;
  private static final int CATS     = 4;
  private static final int ROLES    = 5;
  private static final int PUBROLES = 6;
  private static final int PREVIEW  = 7;

  private int mode = NONE;
  private Document channelTypes = null;
  private Document channelDecl = null;
  private Document chanDoc = null;
  private String action = null;
  private String currentStep = "1";
  private int numSteps;
  private String declURI;
  private String catID[] = null;
  private boolean modified = false; // modification flag
  public static Vector vReservedParams = getReservedParams();
  private Hashtable hParams = null;

  /** Construct a CPublisher.
   */
  public CPublisher ()
  {
    this.staticData = new ChannelStaticData ();
    this.runtimeData = new ChannelRuntimeData ();
    this.set = new StylesheetSet (stylesheetDir + fs + "CPublisher.ssl");
    this.set.setMediaProps (portalBaseDir + fs + "properties" + fs + "media.properties");
    this.chanReg = new ChannelRegistryImpl ();
  }

  /**
   * Loads the reserved parameter names.
   */
    private static Vector getReservedParams()
    {
        Vector v = new Vector();
        v.addElement("action");
        v.addElement("currentStep");
        v.addElement("numSteps");
        v.addElement("ssl");
        return v;
    }

  /** Returns channel runtime properties
   * @return handle to runtime properties
   */
  public ChannelRuntimeProperties getRuntimeProperties ()
  {
    // Channel will always render, so the default values are ok
    return new ChannelRuntimeProperties ();
  }

  /** Receive any events from the layout
   * @param ev layout event
   */
  public void receiveEvent (LayoutEvent ev)
  {
    // no events for this channel
  }

  /** Receive static channel data from the portal
   * @param sd static channel data
   */
  public void setStaticData(final org.jasig.portal.ChannelStaticData sd) throws org.jasig.portal.PortalException
  {
    this.staticData = sd;
  }

  /** Receives channel runtime data from the portal and processes actions
   * passed to it.  The names of these parameters are entirely up to the channel.
   * @param rd handle to channel runtime data
   */
  public void setRuntimeData(final org.jasig.portal.ChannelRuntimeData rd) throws org.jasig.portal.PortalException
  {
    this.runtimeData = rd;

    //catID = runtimeData.getParameter("catID");
    String role = "student"; //need to get from current user
    chanReg = new ChannelRegistryImpl();

    //get fresh copy of both since we don't really know if changes have been made

    if (channelTypes==null) channelTypes = chanReg.getTypesXML(role);

    action = runtimeData.getParameter ("action");
    //System.out.println("action: "+ action);
    if (action != null)
    {
      if (action.equals ("choose"))
        prepareChoose ();
      else if (action.equals ("publish"))
        preparePublish ();
      else if (action.equals ("publishCats"))
        preparePublishCats ();
      else if (action.equals ("saveChanges"))
        prepareSaveChanges ();
      else if (action.equals("cancel"))
          mode = NONE;
    }

  }


  /** Output channel content to the portal
   * @param out a sax document handler
   */
  public void renderXML(final org.xml.sax.DocumentHandler out) throws org.jasig.portal.PortalException
  {
    try
    {
      switch (mode)
      {
        case CHOOSE:
          processXML ("main", out);
          break;
        case PUBLISH:
          processXML ("main", out);
          break;
        default:
          processXML ("main", out);
          break;
      }
    }
    catch (Exception e)
    {
      Logger.log (Logger.ERROR, e);
    }
  }

  private void processXML (String stylesheetName, DocumentHandler out) throws org.xml.sax.SAXException
  {
    XSLTInputSource xmlSource = null;

    switch (mode)
      {
        case CHOOSE:
            xmlSource = new XSLTInputSource(channelDecl);
        case PUBLISH:
          xmlSource = new XSLTInputSource (channelDecl);
          break;
        case CATS:
            xmlSource = new XSLTInputSource(chanReg.getCategoryXML(null));
            break;
        default:
          xmlSource = new XSLTInputSource (channelTypes);
          break;
      }

    XSLTInputSource xslSource = runtimeData.getStylesheet(stylesheetName, set);
    XSLTResultTarget xmlResult = new XSLTResultTarget(out);

    if (xslSource != null)
    {
      XSLTProcessor processor = XSLTProcessorFactory.getProcessor (new org.apache.xalan.xpath.xdom.XercesLiaison ());
      processor.setStylesheetParam("baseActionURL", processor.createXString (runtimeData.getBaseActionURL()));
      processor.setStylesheetParam("currentStep", processor.createXString (currentStep));
      processor.setStylesheetParam("modified", processor.createXBoolean (modified));
      processor.process (xmlSource, xslSource, xmlResult);
    }
    else
      Logger.log(Logger.ERROR, "org.jasig.portal.channels.CPublisher: unable to find a stylesheet for rendering");
  }

  private void prepareChoose ()
  {
    mode = CHOOSE;
    currentStep = "1";
    catID = null;
    String runtimeURI = runtimeData.getParameter ("channel");

    if (runtimeURI != null)
      declURI = runtimeURI;

    try{
        org.apache.xerces.parsers.DOMParser parser = new org.apache.xerces.parsers.DOMParser ();
        parser.parse(UtilitiesBean.fixURI(declURI));
        //System.out.println("declURI: "+ UtilitiesBean.fixURI(declURI));
        channelDecl = parser.getDocument();
    }
    catch (Exception e) {}
  }

  private void preparePublish ()
  {
    mode = PUBLISH;
    if (hParams==null) hParams = new Hashtable();
    currentStep = runtimeData.getParameter("currentStep");
    numSteps = Integer.parseInt(runtimeData.getParameter("numSteps"));
    Enumeration e = runtimeData.getParameterNames();

    if(!currentStep.equals("end")) {

        int i = Integer.parseInt(currentStep);

        if(i < numSteps){
            currentStep = Integer.toString(i+1);
        }
        else if(i == numSteps){
            mode = CATS;
            currentStep = Integer.toString(i+1);
        }
       // else if(i == numSteps + 1) {
       //     mode = ROLES;
       //     currentStep = Integer.toString(i+1);
       // }
        else {
            publishChannel();
            currentStep = "end";
        }

    //System.out.println("numSteps: "+ numSteps);
    //System.out.println("currentStep: "+ currentStep);

    while(e.hasMoreElements()) {
        String s = (String)e.nextElement();

        if(!vReservedParams.contains(s)){
            if (runtimeData.getParameter(s)!=null) {
                //System.out.println("adding param: "+ s);
                //System.out.println("adding param value: "+ runtimeData.getParameter(s));
            hParams.put(s, runtimeData.getParameter(s));
            }
        }
    }
  }
  }

    private void publishChannel () {

        String nextID = chanReg.getNextId();
        Document doc = new DocumentImpl();
        Element chan = doc.createElement("channel");
        chan.setAttribute("timeout", "5000");
        chan.setAttribute("priority", "1");
        chan.setAttribute("minimized", "false");
        chan.setAttribute("editable", "false");
        chan.setAttribute("hasHelp", "false");
        chan.setAttribute("removable", "true");
        chan.setAttribute("detachable", "true");
        chan.setAttribute("class", (String)hParams.get("class"));
        if (nextID!=null) chan.setAttribute("ID", "chan"+nextID);

        Enumeration e = hParams.keys();
        while (e.hasMoreElements()) {
            String name = (String)e.nextElement();
            String value = (String) hParams.get(name);
            Element el = doc.createElement("parameter");
            el.setAttribute(XMLEscaper.escape(name), XMLEscaper.escape(value));
            chan.appendChild(el);
        }
        doc.appendChild(chan);

        chanReg.addChannel(nextID, "new channel", doc, catID);
    }


  private void preparePublishCats ()
  {
    mode = PUBLISH;
    catID = runtimeData.getParameterValues("cat");
  }

  private void prepareSaveChanges ()
  {
    // save layout copy
    catID = this.catID;
    modified = false;
  }

  public void setPortalControlStructures(final org.jasig.portal.PortalControlStructures p1) throws org.jasig.portal.PortalException {
  }

}
