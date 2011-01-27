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

package org.exoplatform.portal.webui.workspace;

import java.lang.reflect.Method;

import org.exoplatform.portal.application.PortalRequestContext;
import org.exoplatform.portal.config.UserACL;
import org.exoplatform.portal.config.UserPortalConfig;
import org.exoplatform.portal.config.UserPortalConfigService;
import org.exoplatform.portal.config.model.Page;
import org.exoplatform.portal.config.model.PageNavigation;
import org.exoplatform.portal.config.model.PortalConfig;
import org.exoplatform.portal.webui.page.UIPage;
import org.exoplatform.portal.webui.page.UIPageBody;
import org.exoplatform.portal.webui.page.UIPageCreationWizard;
import org.exoplatform.portal.webui.page.UISiteBody;
import org.exoplatform.portal.webui.page.UIWizardPageSetInfo;
import org.exoplatform.portal.webui.portal.UIPortal;
import org.exoplatform.portal.webui.portal.UIPortalComposer;
import org.exoplatform.portal.webui.portal.UIPortalForm;
import org.exoplatform.portal.webui.util.PortalDataMapper;
import org.exoplatform.portal.webui.util.Util;
import org.exoplatform.web.application.ApplicationMessage;
import org.exoplatform.webui.application.WebuiRequestContext;
import org.exoplatform.webui.event.Event;
import org.exoplatform.webui.event.EventListener;

/**
 * Created by The eXo Platform SAS 
 * 
 * Author : Pham Thanh Tung
 * thanhtungty@gmail.com May 5, 2009
 */
public class UIMainActionListener
{

   static public class EditCurrentPageActionListener extends EventListener<UIWorkingWorkspace>
   {
      public void execute(Event<UIWorkingWorkspace> event) throws Exception
      {
         UIPortalApplication uiApp = Util.getUIPortalApplication();
         UIWorkingWorkspace uiWorkingWS = uiApp.getChildById(UIPortalApplication.UI_WORKING_WS_ID);

         // check edit permission for page
         UIPageBody pageBody = uiWorkingWS.findFirstComponentOfType(UIPageBody.class);
         UIPage uiPage = (UIPage)pageBody.getUIComponent();
         if (uiPage == null)
         {
            uiApp.addMessage(new ApplicationMessage("UIPageBrowser.msg.PageNotExist", null));
            return;
         }
         Page page = PortalDataMapper.toPageModel(uiPage);

         UserACL userACL = uiApp.getApplicationComponent(UserACL.class);
         if (!userACL.hasEditPermission(page))
         {
            uiApp.addMessage(new ApplicationMessage("UIPortalManagement.msg.Invalid-EditPage-Permission", null));
            return;
         }

         uiWorkingWS.setRenderedChild(UIEditInlineWorkspace.class);

         UIPortalComposer portalComposer =
            uiWorkingWS.findFirstComponentOfType(UIPortalComposer.class).setRendered(true);
         portalComposer.setComponentConfig(UIPortalComposer.class, "UIPageEditor");
         portalComposer.setId("UIPageEditor");
         portalComposer.setShowControl(true);
         portalComposer.setEditted(false);
         portalComposer.setCollapse(false);

         UIPortalToolPanel uiToolPanel = uiWorkingWS.findFirstComponentOfType(UIPortalToolPanel.class);
         uiToolPanel.setShowMaskLayer(false);
         uiApp.setModeState(UIPortalApplication.APP_BLOCK_EDIT_MODE);

         // We clone the edited UIPage object, that is required for Abort action
         Class<? extends UIPage> clazz = Class.forName(page.getFactoryId()).asSubclass(UIPage.class);
         UIPage newUIPage = uiWorkingWS.createUIComponent(clazz, null, null);
         PortalDataMapper.toUIPage(newUIPage, page);
         uiToolPanel.setWorkingComponent(newUIPage);

         // Remove current UIPage from UIPageBody
         pageBody.setUIComponent(null);

         event.getRequestContext().addUIComponentToUpdateByAjax(uiWorkingWS);
         Util.getPortalRequestContext().setFullRender(true);
      }
   }

   static public class PageCreationWizardActionListener extends EventListener<UIWorkingWorkspace>
   {
      public void execute(Event<UIWorkingWorkspace> event) throws Exception
      {
         UIPortalApplication uiApp = Util.getUIPortalApplication();
         uiApp.setModeState(UIPortalApplication.APP_BLOCK_EDIT_MODE);
         UIWorkingWorkspace uiWorkingWS = uiApp.getChildById(UIPortalApplication.UI_WORKING_WS_ID);
         
         if (!hasPageCreationPermission())
         {
            uiApp.addMessage(new ApplicationMessage("UIPortalManagement.msg.Invalid-CreatePage-Permission", null));
            return;
         }

         uiWorkingWS.setRenderedChild(UIEditInlineWorkspace.class);

         UIPortalComposer portalComposer = uiWorkingWS.findFirstComponentOfType(UIPortalComposer.class);
         portalComposer.setRendered(false);
         portalComposer.setComponentConfig(UIPortalComposer.class, "UIPageEditor");
         portalComposer.setId("UIPageEditor");
         portalComposer.setShowControl(true);
         portalComposer.setEditted(true);
         portalComposer.setCollapse(false);

         UIPortalToolPanel uiToolPanel = uiWorkingWS.findFirstComponentOfType(UIPortalToolPanel.class);
         uiToolPanel.setShowMaskLayer(false);
         uiToolPanel.setWorkingComponent(UIPageCreationWizard.class, null);
         UIPageCreationWizard uiWizard = (UIPageCreationWizard)uiToolPanel.getUIComponent();
         UIWizardPageSetInfo uiPageSetInfo = uiWizard.getChild(UIWizardPageSetInfo.class);
         uiPageSetInfo.setShowPublicationDate(false);
         event.getRequestContext().addUIComponentToUpdateByAjax(uiWorkingWS);
      }
      
      private boolean hasPageCreationPermission() throws Exception
      {
         UIPortal currentPortal = Util.getUIPortal();
         UserACL userACL = Util.getUIPortalApplication().getApplicationComponent(UserACL.class);
         PageNavigation selectedNavigation = currentPortal.getSelectedNavigation();
         if (PortalConfig.PORTAL_TYPE.equals(selectedNavigation.getOwnerType()))
         {
            return userACL.hasEditPermissionOnPortal(currentPortal.getOwnerType(), currentPortal.getOwner(), currentPortal.getEditPermission());
         }
         
         return userACL.hasEditPermission(selectedNavigation);
      }
   }

   static public class EditInlineActionListener extends EventListener<UIWorkingWorkspace>
   {
      public void execute(Event<UIWorkingWorkspace> event) throws Exception
      {
         UIPortalApplication uiApp = Util.getUIPortalApplication();

         UIPortal uiPortal = uiApp.getShowedUIPortal();

         UserPortalConfigService service = uiApp.getApplicationComponent(UserPortalConfigService.class);
         UserPortalConfig userConfig =
            service.getUserPortalConfig(uiPortal.getName(), event.getRequestContext().getRemoteUser());
         if (userConfig == null)
            userConfig = uiApp.getUserPortalConfig();
         
         //Todo nguyenanhkien2a@gmail.com
         //Check editing permission
         UIPortalApplication portalApp = Util.getUIPortalApplication();
         UIPortal currentUIPortal = portalApp.<UIWorkingWorkspace>findComponentById(
            UIPortalApplication.UI_WORKING_WS_ID).findFirstComponentOfType(UIPortal.class);
         UserACL userACL = portalApp.getApplicationComponent(UserACL.class);
         if(!userACL.hasEditPermissionOnPortal(currentUIPortal.getOwnerType(), currentUIPortal.getOwner(), 
                                                currentUIPortal.getEditPermission()))
         {
            uiApp.addMessage(new ApplicationMessage("UIPortalManagement.msg.Invalid-EditLayout-Permission",
               new String[]{uiPortal.getName()}));
            return;
         }
         
         PortalRequestContext pcontext = (PortalRequestContext)event.getRequestContext();
         UIWorkingWorkspace uiWorkingWS = uiApp.getChildById(UIPortalApplication.UI_WORKING_WS_ID);
         uiWorkingWS.setBackupUIPortal(uiPortal);
         uiApp.setModeState(UIPortalApplication.APP_BLOCK_EDIT_MODE);

         UIPortal newPortal = uiWorkingWS.createUIComponent(UIPortal.class, null, null);
         PortalDataMapper.toUIPortal(newPortal, userConfig);
         newPortal.setSelectedNode(uiPortal.getSelectedNode());
         newPortal.setSelectedNavigation(uiPortal.getSelectedNavigation());
         newPortal.setSelectedPath(uiPortal.getSelectedPath());
         newPortal.refreshUIPage();

         UIEditInlineWorkspace uiEditWS = uiWorkingWS.getChild(UIEditInlineWorkspace.class);
         uiEditWS.setUIComponent(newPortal);
         UISiteBody siteBody = uiWorkingWS.findFirstComponentOfType(UISiteBody.class);
         siteBody.setUIComponent(null);

         UIPortalComposer uiComposer = uiEditWS.getComposer().setRendered(true);
         uiComposer.setComponentConfig(UIPortalComposer.class, null);
         uiComposer.setShowControl(true);
         uiComposer.setEditted(false);
         uiComposer.setCollapse(false);
         uiComposer.setId("UIPortalComposer");

         uiWorkingWS.setRenderedChild(UIEditInlineWorkspace.class);
         pcontext.addUIComponentToUpdateByAjax(uiWorkingWS);
         pcontext.setFullRender(true);
      }
   }

   public static class CreatePortalActionListener extends EventListener<UIWorkingWorkspace>
   {
      public void execute(Event<UIWorkingWorkspace> event) throws Exception
      {
         PortalRequestContext prContext = Util.getPortalRequestContext();
         UIPortalApplication uiApp = event.getSource().getAncestorOfType(UIPortalApplication.class);
         UserACL userACL = uiApp.getApplicationComponent(UserACL.class);
         if (!userACL.hasCreatePortalPermission())
         {
            uiApp.addMessage(new ApplicationMessage("UIPortalBrowser.msg.Invalid-createPermission", null));
            return;
         }
         UIMaskWorkspace uiMaskWS = uiApp.getChildById(UIPortalApplication.UI_MASK_WS_ID);
         UIPortalForm uiNewPortal = uiMaskWS.createUIComponent(UIPortalForm.class, "CreatePortal", "UIPortalForm");
         uiMaskWS.setUIComponent(uiNewPortal);
         uiMaskWS.setShow(true);
         prContext.addUIComponentToUpdateByAjax(uiMaskWS);
      }
   }
   
   public static class EditBackgroundActionListener extends EventListener<UIWorkingWorkspace>
   {
      @Override
      public void execute(Event<UIWorkingWorkspace> event) throws Exception
      {
         
         UIWorkingWorkspace workingWorkspace = event.getSource();
         UIPage uiPage = workingWorkspace.findFirstComponentOfType(UIPage.class);
         
         Method showEditBackgroundPopupMethod = uiPage.getClass().getDeclaredMethod("showEditBackgroundPopup", WebuiRequestContext.class);
         if(showEditBackgroundPopupMethod != null)
         {
            showEditBackgroundPopupMethod.invoke(uiPage, event.getRequestContext());
         }
      }
   }

}
