/**
 * Copyright (C) 2009 eXo Platform SAS.
 * 
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 * 
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 * 
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */

package org.exoplatform.toolbar.webui.component;

import org.exoplatform.portal.config.DataStorage;
import org.exoplatform.portal.config.UserACL;
import org.exoplatform.portal.config.model.Page;
import org.exoplatform.portal.config.model.PageNavigation;
import org.exoplatform.portal.config.model.PageNode;
import org.exoplatform.portal.config.model.PortalConfig;
import org.exoplatform.portal.webui.page.UIPage;
import org.exoplatform.portal.webui.page.UIPageBody;
import org.exoplatform.portal.webui.portal.UIPortal;
import org.exoplatform.portal.webui.util.Util;
import org.exoplatform.portal.webui.workspace.UIPortalApplication;
import org.exoplatform.portal.webui.workspace.UIWorkingWorkspace;
import org.exoplatform.webui.application.WebuiApplication;
import org.exoplatform.webui.application.WebuiRequestContext;
import org.exoplatform.webui.config.annotation.ComponentConfig;
import org.exoplatform.webui.core.UIPortletApplication;
import org.exoplatform.webui.core.lifecycle.UIApplicationLifecycle;

@ComponentConfig(lifecycle = UIApplicationLifecycle.class, template = "app:/groovy/admintoolbar/webui/component/UIAdminToolbarPortlet.gtmpl")
public class UIAdminToolbarPortlet extends UIPortletApplication
{

   public UIAdminToolbarPortlet() throws Exception
   {
   }

   public PageNavigation getSelectedNavigation() throws Exception
   {
      return Util.getUIPortal().getSelectedNavigation();
   }

   @Override
   public void processRender(WebuiApplication app, WebuiRequestContext context) throws Exception
   {
      // A user could view the toolbar portlet iff he/she has edit permission
      // either on
      // 'active' page, 'active' portal or 'active' navigation
      if (hasEditPermissionOnNavigation() || hasEditPermissionOnPage() || hasEditPermissionOnPortal())
      {
         super.processRender(app, context);
      }
   }

   private boolean hasEditPermissionOnNavigation() throws Exception
   {
      PageNavigation selectedNavigation = getSelectedNavigation();
      UIPortalApplication portalApp = Util.getUIPortalApplication();
      UserACL userACL = portalApp.getApplicationComponent(UserACL.class);
      if (selectedNavigation == null || userACL == null)
      {
         return false;
      }
      else
      {
         if (PortalConfig.PORTAL_TYPE.equals(selectedNavigation.getOwnerType()))
         {
            return hasEditPermissionOnPortal();
         }
         return userACL.hasEditPermission(selectedNavigation);
      }
   }
   
   private boolean hasEditPermissionOnPortal() throws Exception
   {
      UIPortalApplication portalApp = Util.getUIPortalApplication();
      UIPortal currentUIPortal = portalApp.<UIWorkingWorkspace>findComponentById(UIPortalApplication.UI_WORKING_WS_ID).findFirstComponentOfType(UIPortal.class);
      UserACL userACL = portalApp.getApplicationComponent(UserACL.class);
      return userACL.hasEditPermissionOnPortal(currentUIPortal.getOwnerType(), currentUIPortal.getOwner(), currentUIPortal.getEditPermission());
   }

   private boolean hasEditPermissionOnPage() throws Exception
   {
      UIPortalApplication portalApp = Util.getUIPortalApplication();
      UIWorkingWorkspace uiWorkingWS = portalApp.getChildById(UIPortalApplication.UI_WORKING_WS_ID);
      UIPageBody pageBody = uiWorkingWS.findFirstComponentOfType(UIPageBody.class);
      UIPage uiPage = (UIPage)pageBody.getUIComponent();
      UserACL userACL = portalApp.getApplicationComponent(UserACL.class);

      if(uiPage != null)
      {
         return userACL.hasEditPermissionOnPage(uiPage.getOwnerType(), uiPage.getOwnerId(), uiPage.getEditPermission());
      }
      else
      {
         UIPortal currentUIPortal = portalApp.<UIWorkingWorkspace>findComponentById(UIPortalApplication.UI_WORKING_WS_ID).findFirstComponentOfType(UIPortal.class);
         PageNode currentNode = currentUIPortal.getSelectedNode();
         String pageReference = currentNode.getPageReference();
         if(pageReference == null)
         {
            return false;
         }
         else
         {
            DataStorage dataStorage = portalApp.getApplicationComponent(DataStorage.class);
            Page page = dataStorage.getPage(pageReference);
            if(page == null)
            {
               return false;
            }
            else
            {
               return userACL.hasEditPermission(page);
            }
         }
      }
   }
}
