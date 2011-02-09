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

package org.exoplatform.portal.webui.page;

import org.exoplatform.commons.serialization.api.annotations.Serialized;
import org.exoplatform.commons.utils.PageList;
import org.exoplatform.commons.utils.PageListAccess;
import org.exoplatform.portal.application.PortalRequestContext;
import org.exoplatform.portal.config.DataStorage;
import org.exoplatform.portal.config.Query;
import org.exoplatform.portal.config.UserPortalConfigService;
import org.exoplatform.portal.config.model.ModelObject;
import org.exoplatform.portal.config.model.Page;
import org.exoplatform.portal.config.model.PageNavigation;
import org.exoplatform.portal.config.model.PageNode;
import org.exoplatform.portal.config.model.PortalConfig;
import org.exoplatform.portal.webui.portal.PageNodeEvent;
import org.exoplatform.portal.webui.portal.UIPortal;
import org.exoplatform.portal.webui.util.PortalDataMapper;
import org.exoplatform.portal.webui.util.Util;
import org.exoplatform.portal.webui.workspace.UIMaskWorkspace;
import org.exoplatform.portal.webui.workspace.UIPortalApplication;
import org.exoplatform.portal.webui.workspace.UIWorkingWorkspace;
import org.exoplatform.web.application.ApplicationMessage;
import org.exoplatform.webui.application.WebuiRequestContext;
import org.exoplatform.webui.config.annotation.ComponentConfig;
import org.exoplatform.webui.config.annotation.ComponentConfigs;
import org.exoplatform.webui.config.annotation.EventConfig;
import org.exoplatform.webui.config.annotation.ParamConfig;
import org.exoplatform.webui.core.UIApplication;
import org.exoplatform.webui.core.UIComponent;
import org.exoplatform.webui.core.UIPopupWindow;
import org.exoplatform.webui.core.UIRepeater;
import org.exoplatform.webui.core.UISearch;
import org.exoplatform.webui.core.UIVirtualList;
import org.exoplatform.webui.core.lifecycle.UIFormLifecycle;
import org.exoplatform.webui.core.model.SelectItemOption;
import org.exoplatform.webui.event.Event;
import org.exoplatform.webui.event.EventListener;
import org.exoplatform.webui.event.Event.Phase;
import org.exoplatform.webui.form.UIForm;
import org.exoplatform.webui.form.UIFormInputItemSelector;
import org.exoplatform.webui.form.UIFormInputSet;
import org.exoplatform.webui.form.UIFormSelectBox;
import org.exoplatform.webui.form.UIFormStringInput;
import org.exoplatform.webui.form.UISearchForm;

import java.util.ArrayList;
import java.util.List;
import java.util.ResourceBundle;

@ComponentConfigs({
   @ComponentConfig(template = "system:/groovy/portal/webui/page/UIPageBrowser.gtmpl", events = {
      @EventConfig(listeners = UIPageBrowser.DeleteActionListener.class, confirm = "UIPageBrowse.deletePage"),
      @EventConfig(listeners = UIPageBrowser.EditInfoActionListener.class),
      @EventConfig(listeners = UIPageBrowser.AddNewActionListener.class)
   //        @EventConfig(listeners = UIPageBrowser.BackActionListener.class)
   }),
   @ComponentConfig(id = "UIBrowserPageForm", type = UIPageForm.class, lifecycle = UIFormLifecycle.class, template = "system:/groovy/webui/form/UIFormTabPane.gtmpl", events = {
      @EventConfig(listeners = UIPageBrowser.SavePageActionListener.class, name = "Save"),
      @EventConfig(listeners = UIPageForm.ChangeOwnerTypeActionListener.class, phase = Phase.DECODE),
      @EventConfig(listeners = UIPageForm.ChangeOwnerIdActionListener.class, phase = Phase.DECODE),
      @EventConfig(listeners = UIPageForm.SelectMembershipActionListener.class, phase = Phase.DECODE),
      @EventConfig(listeners = UIMaskWorkspace.CloseActionListener.class, phase = Phase.DECODE)}, initParams = @ParamConfig(name = "PageTemplate", value = "system:/WEB-INF/conf/uiconf/portal/webui/page/PageTemplate.groovy")),
   @ComponentConfig(type = UIFormInputSet.class, id = "PermissionSetting", template = "system:/groovy/webui/core/UITabSelector.gtmpl", events = {@EventConfig(listeners = UIFormInputSet.SelectComponentActionListener.class)})})
@Serialized
public class UIPageBrowser extends UISearch
{

   public static final String[] BEAN_FIELD = {"pageId", "title", "accessPermissions", "editPermission"};

   public static final String[] ACTIONS = {"EditInfo", "Delete"};

   private boolean showAddNewPage = false;

   protected String pageSelectedId_;

   private static List<SelectItemOption<String>> OPTIONS = new ArrayList<SelectItemOption<String>>(3);

   static
   {
      WebuiRequestContext contextui = WebuiRequestContext.getCurrentInstance();
      ResourceBundle res = contextui.getApplicationResourceBundle();
      OPTIONS.add(new SelectItemOption<String>(res.getString("UIPageSearch.label.option.ownerType"), "ownerType"));
      OPTIONS.add(new SelectItemOption<String>(res.getString("UIPageSearch.label.option.ownerId"), "ownerId"));
      OPTIONS.add(new SelectItemOption<String>(res.getString("UIPageSearch.label.option.title"), "title"));
   }

   private Query<Page> lastQuery_;

   public UIPageBrowser() throws Exception
   {
      super(OPTIONS);

      getChild(UISearchForm.class).setId("UIPageSearch");
      UIRepeater uiRepeater = createUIComponent(UIRepeater.class, null, null);
      uiRepeater.configure("pageId", BEAN_FIELD, ACTIONS);

      UIVirtualList virtualList = addChild(UIVirtualList.class, null, null);
      virtualList.setPageSize(10);
      virtualList.setUIComponent(uiRepeater);
   }

   public Query<Page> getLastQuery()
   {
      return lastQuery_;
   }

   /**
    * Update data feed in UIRepeater with a given query.
    * Returns false if no result is found, true other wise
    * 
    * @param query
    * @return
    * @throws Exception
    */
   public boolean feedDataWithQuery(Query<Page> query) throws Exception
   {
      lastQuery_ = query;

      UIVirtualList virtualList = getChild(UIVirtualList.class);
      if (lastQuery_ == null)
      {
         lastQuery_ = new Query<Page>(null, null, null, null, Page.class);
      }
      virtualList.dataBind(new PageQueryAccessList(lastQuery_, 10));

      UIRepeater repeater = (UIRepeater)virtualList.getDataFeed();
      PageList datasource = repeater.getDataSource();

      if (datasource.getAvailable() > 0)
      {
         return true;
      }
      else
      {
         return false;
      }
   }

   /**
    * Show a popup informing that no result available for the last query
    *
    */
   public static void showNoResultMessagePopup()
   {
      UIApplication uiApp = Util.getPortalRequestContext().getUIApplication();
      uiApp.addMessage(new ApplicationMessage("UISearchForm.msg.empty", null));
      Util.getPortalRequestContext().addUIComponentToUpdateByAjax(uiApp.getUIPopupMessages());
   }

   public void quickSearch(UIFormInputSet quickSearchInput) throws Exception
   {
      UIFormStringInput input = (UIFormStringInput)quickSearchInput.getChild(0);
      UIFormSelectBox select = (UIFormSelectBox)quickSearchInput.getChild(1);
      String value = input.getValue();
      String selectBoxValue = select.getValue();
      Query<Page> query = new Query<Page>(null, null, null, null, Page.class);
      if (selectBoxValue.equals("title"))
         query.setTitle(value);
      else if (selectBoxValue.equals("ownerType"))
         query.setOwnerType(value);
      else if (selectBoxValue.equals("ownerId"))
         query.setOwnerId(value);
      query.setName(null);

      lastQuery_ = query;
      boolean dataAvailable = feedDataWithQuery(lastQuery_);
      if (!dataAvailable)
      {
         showNoResultMessagePopup();
      }

      if (this.<UIComponent> getParent() instanceof UIPopupWindow)
      {
         UIPopupWindow popupWindow = getParent();
         popupWindow.setShow(true);
      }
   }

   public boolean isShowAddNewPage()
   {
      return showAddNewPage;
   }

   public void setShowAddNewPage(boolean showAddNewPage)
   {
      this.showAddNewPage = showAddNewPage;
   }

   public void processDecode(WebuiRequestContext context) throws Exception
   {
      super.processDecode(context);
      UIForm uiForm = getAncestorOfType(UIForm.class);
      String action = null;
      if (uiForm != null)
      {
         action = uiForm.getSubmitAction();
      }
      else
      {
         action = context.getRequestParameter(UIForm.ACTION);
      }
      if (action == null)
         return;
      Event<UIComponent> event = createEvent(action, Event.Phase.PROCESS, context);
      if (event != null)
         event.broadcast();
   }

   public void advancedSearch(UIFormInputSet advancedSearchInput) throws Exception
   {
   }

   /*
   public void reset() throws Exception
   {
      UIVirtualList virtualList = getChild(UIVirtualList.class);
      UIRepeater repeater = (UIRepeater)virtualList.getDataFeed();
      LazyPageList datasource = (LazyPageList)repeater.getDataSource();
      int currentPage = datasource.getCurrentPage();
      feedDataWithQuery(null);
      if (currentPage > datasource.getAvailablePage())
         currentPage = datasource.getAvailablePage();
      if (currentPage > 0)
         datasource.getPage(currentPage);
   }
   */

   static public class DeleteActionListener extends EventListener<UIPageBrowser>
   {
      public void execute(Event<UIPageBrowser> event) throws Exception
      {
         UIPageBrowser uiPageBrowser = event.getSource();
         PortalRequestContext pcontext = Util.getPortalRequestContext();
         String id = pcontext.getRequestParameter(OBJECTID);
         UserPortalConfigService service = uiPageBrowser.getApplicationComponent(UserPortalConfigService.class);
         DataStorage dataService = uiPageBrowser.getApplicationComponent(DataStorage.class);

         UIPortalApplication uiPortalApp = (UIPortalApplication)pcontext.getUIApplication();
         if (service.getPage(id) == null)
         {
            uiPortalApp.addMessage(new ApplicationMessage("UIPageBrowser.msg.PageNotExist", new String[]{id}, 1));
            pcontext.addUIComponentToUpdateByAjax(uiPortalApp.getUIPopupMessages());
            return;
         }
         Page page = service.getPage(id, pcontext.getRemoteUser());
         if (page != null && page.getName().equals("webos"))
         {
            uiPortalApp.addMessage(new ApplicationMessage("UIPageBrowser.msg.delete.NotDeleteDesktopPage", new String[]{id}, 1));
            pcontext.addUIComponentToUpdateByAjax(uiPortalApp.getUIPopupMessages());
            return;
         }

         if (page == null || !page.isModifiable())
         {
            uiPortalApp.addMessage(new ApplicationMessage("UIPageBrowser.msg.delete.NotDelete", new String[]{id}, 1));
            pcontext.addUIComponentToUpdateByAjax(uiPortalApp.getUIPopupMessages());
            return;
         }
         
         UIVirtualList virtualList = uiPageBrowser.getChild(UIVirtualList.class);
         UIRepeater repeater = (UIRepeater)virtualList.getDataFeed();
         PageListAccess datasource = (PageListAccess)repeater.getDataSource();
         int currentPage = datasource.getCurrentPage();

         dataService.remove(page);
         //Minh Hoang TO: The cached UIPage objects corresponding to removed Page should be removed here.
         //As we have multiple UIPortal, which means multiple caches of UIPage. It 's unwise to garbage
         // all UIPage caches at once. Better solution is to clear UIPage on browsing to PageNode having Page
         //removed

         UIPortal uiPortal = Util.getUIPortal();
         if (uiPortal.getSelectedNode().getPageReference().equals(page.getPageId()))
         {
            PageNodeEvent<UIPortal> pnevent =
               new PageNodeEvent<UIPortal>(uiPortal, PageNodeEvent.CHANGE_PAGE_NODE, uiPortal.getSelectedNode()
                  .getUri());
            uiPortal.broadcast(pnevent, Phase.PROCESS);
         }
         else
         {
            boolean dataAvailable = uiPageBrowser.feedDataWithQuery(uiPageBrowser.getLastQuery());
            if (!dataAvailable)
            {
               showNoResultMessagePopup();
            }
            if (currentPage > datasource.getAvailablePage())
               currentPage = datasource.getAvailablePage();
            datasource.getPage(currentPage);
            event.getRequestContext().addUIComponentToUpdateByAjax(uiPageBrowser);
         }
         
         //Update navigation and UserToolbarGroupPortlet if deleted page is dashboard page
         if(page.getOwnerType().equals(PortalConfig.USER_TYPE)){
            removePageNode(page, event);
         }
      }
      
      /**
       * 
       * This method remove User Page node that reference to page.
       * If page is exist, remove User Page node.
       * If page is not exist, do nothing.
       * 
       * @param page  the page is referenced by User Page node
       * @param event
       * @throws Exception any exception
       */
      private void removePageNode(Page page, Event<UIPageBrowser> event) throws Exception
      {
         UIPageBrowser uiPageBrowser = event.getSource();
         DataStorage dataService = uiPageBrowser.getApplicationComponent(DataStorage.class);

         PageNavigation pageNavigation = null;
         UIPortalApplication portalApplication = Util.getUIPortalApplication();

         List<PageNavigation> listPageNavigation = portalApplication.getNavigations();

         for (PageNavigation pageNvg : listPageNavigation)
         {
            if (pageNvg.getOwnerType().equals(PortalConfig.USER_TYPE))
            {
               pageNavigation = pageNvg;
               break;
            }
         }
         UIPortal uiPortal = Util.getUIPortal();

         PageNode tobeRemoved = null;
         List<PageNode> nodes = pageNavigation.getNodes();
         for (PageNode pageNode : nodes)
         {
            String pageReference = pageNode.getPageReference();
            String pageId = page.getPageId();

            if (pageReference != null && pageReference.equals(pageId))
            {
               tobeRemoved = pageNode;
               break;
            }
         }

         if (tobeRemoved != null)
         {
            // Remove pageNode
            pageNavigation.getNodes().remove(tobeRemoved);

            // Update navigation and UserToolbarGroupPortlet

            String pageRef = tobeRemoved.getPageReference();
            if (pageRef != null && pageRef.length() > 0)
            {
               // Remove from cache
               uiPortal.clearUIPage(pageRef);
            }

            dataService.save(pageNavigation);

            //Update UserToolbarGroupPortlet
            UIWorkingWorkspace uiWorkingWS = portalApplication.getChild(UIWorkingWorkspace.class);
            uiWorkingWS.updatePortletsByName("UserToolbarDashboardPortlet");

         }
      }
   }
   

   static public class EditInfoActionListener extends EventListener<UIPageBrowser>
   {
      public void execute(Event<UIPageBrowser> event) throws Exception
      {
         UIPageBrowser uiPageBrowser = event.getSource();
         WebuiRequestContext context = event.getRequestContext();
         String pageID = context.getRequestParameter(OBJECTID);
         DataStorage service = uiPageBrowser.getApplicationComponent(DataStorage.class);

         //Check existence of the page
         Page page = service.getPage(pageID);
         if (page == null)
         {
            context.getUIApplication().addMessage(new ApplicationMessage("UIPageBrowser.msg.PageNotExist", new String[]{pageID}, 1));
            return;
         }

         //Need this code to override editpage action in extension project
         Class<? extends UIPage> clazz = UIPage.getRealClass(page.getFactoryId());
         clazz.getConstructor().newInstance().switchToEditMode(page);
      }
   }

   static public class AddNewActionListener extends EventListener<UIPageBrowser>
   {
      public void execute(Event<UIPageBrowser> event) throws Exception
      {
         PortalRequestContext prContext = Util.getPortalRequestContext();
         UIPortalApplication uiApp = (UIPortalApplication)prContext.getUIApplication();
         UIMaskWorkspace uiMaskWS = uiApp.getChildById(UIPortalApplication.UI_MASK_WS_ID);
         UIPageForm uiPageForm = uiMaskWS.createUIComponent(UIPageForm.class, "UIBrowserPageForm", "UIPageForm");
         uiMaskWS.setUIComponent(uiPageForm);
         uiMaskWS.setShow(true);
         uiPageForm.getUIStringInput("ownerType").setValue(PortalConfig.USER_TYPE);
         uiPageForm.getUIStringInput("ownerId").setValue(prContext.getRemoteUser());
         uiPageForm.removeChildById("PermissionSetting");
         uiPageForm.removeChild(UIFormInputItemSelector.class);
         UIPageTemplateOptions uiTemplateConfig = uiPageForm.createUIComponent(UIPageTemplateOptions.class, null, null);
         uiPageForm.addUIFormInput(uiTemplateConfig);
         prContext.addUIComponentToUpdateByAjax(uiMaskWS);
      }
   }

   //  TODO: Tan Pham Dinh: No need back action in portal 2.6
   //  static public class BackActionListener extends EventListener<UIPageBrowser> {
   //
   //    public void execute(Event<UIPageBrowser> event) throws Exception {
   //      UIPortalApplication uiPortalApp = Util.getUIPortalApplication();
   //      uiPortalApp.setModeState(UIPortalApplication.NORMAL_MODE);
   //      UIPortal uiPortal = Util.getUIPortal();
   //      String uri = uiPortal.getSelectedNavigation().getId() + "::"
   //          + uiPortal.getSelectedNode().getUri();
   //      PageNodeEvent<UIPortal> pnevent = new PageNodeEvent<UIPortal>(uiPortal,
   //                                                                    PageNodeEvent.CHANGE_PAGE_NODE,
   //                                                                    uri);
   //      uiPortal.broadcast(pnevent, Event.Phase.PROCESS);
   //    }
   //
   //  }

   static public class SavePageActionListener extends UIPageForm.SaveActionListener
   {
      public void execute(Event<UIPageForm> event) throws Exception
      {
         UIPageForm uiPageForm = event.getSource();
         UIPortalApplication uiPortalApp = uiPageForm.getAncestorOfType(UIPortalApplication.class);
         PortalRequestContext pcontext = Util.getPortalRequestContext();
         UIPage uiPage = uiPageForm.getUIPage();
         Page page = new Page();
         uiPageForm.invokeSetBindingBean(page);
         DataStorage dataService = uiPageForm.getApplicationComponent(DataStorage.class);
         // create new page
         if (uiPage == null)
         {
            Page existPage = dataService.getPage(page.getPageId());
            if (existPage != null)
            {
               uiPortalApp.addMessage(new ApplicationMessage("UIPageForm.msg.sameName", null));
               pcontext.addUIComponentToUpdateByAjax(uiPortalApp.getUIPopupMessages());
               return;
            }
            page.setModifiable(true);
            if (page.getChildren() == null)
               page.setChildren(new ArrayList<ModelObject>());
            dataService.create(page);
            postSave(uiPortalApp, pcontext);
            return;
         }

         page.setOwnerType(uiPage.getOwnerType());

         List<UIComponent> uiChildren = uiPage.getChildren();
         if (uiChildren == null)
            return;
         ArrayList<ModelObject> children = new ArrayList<ModelObject>();
         for (UIComponent child : uiChildren)
         {
            ModelObject component = PortalDataMapper.buildModelObject(child);
            if (component != null)
               children.add(component);
         }
         page.setChildren(children);
         uiPage.getChildren().clear();

         PortalDataMapper.toUIPage(uiPage, page);
      }

      private void postSave(UIPortalApplication uiPortalApp, WebuiRequestContext context) throws Exception
      {
         UIMaskWorkspace uiMaskWS = uiPortalApp.getChildById(UIPortalApplication.UI_MASK_WS_ID);
         uiMaskWS.setUIComponent(null);
         uiMaskWS.setShow(false);
         context.addUIComponentToUpdateByAjax(uiMaskWS);

         UIWorkingWorkspace uiWorkWS = uiPortalApp.getChildById(UIPortalApplication.UI_WORKING_WS_ID);
         uiWorkWS.updatePortletsByName("PageManagementPortlet");
      }
   }
}
