/*
 * IntentsLab - Android app for playing with Intents and Binder IPC
 * Copyright (C) 2014 Michał Bednarski
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 *     You should have received a copy of the GNU General Public License
 *     along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.github.michalbednarski.intentslab.browser;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;
import android.content.pm.PermissionInfo;
import android.content.pm.ProviderInfo;
import android.os.Build;
import android.os.Bundle;
import android.os.Parcel;
import android.view.Menu;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.Spinner;
import com.github.michalbednarski.intentslab.R;
import com.github.michalbednarski.intentslab.appinfo.MyComponentInfo;
import com.github.michalbednarski.intentslab.appinfo.MyPackageInfo;
import com.github.michalbednarski.intentslab.appinfo.MyPackageManager;
import com.github.michalbednarski.intentslab.appinfo.MyPackageManagerImpl;
import com.github.michalbednarski.intentslab.appinfo.MyPermissionInfo;
import com.github.michalbednarski.intentslab.editor.IntentEditorConstants;

import org.jdeferred.DoneFilter;
import org.jdeferred.Promise;
import org.jdeferred.impl.DefaultDeferredManager;
import org.jdeferred.multiple.MultipleResults;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.Map;

/**
 * Fetcher for application components
 */
public class ComponentFetcher extends Fetcher {
    private static final String TAG = "ComponentFetcher";

    static final boolean DEVELOPMENT_PERMISSIONS_SUPPORTED =
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN;


    /**
     * Type of components to get
     *
     * Combination of following flags:
     * <ul>
     *  <li> {@link PackageManager#GET_ACTIVITIES}
     *  <li> {@link PackageManager#GET_RECEIVERS}
     *  <li> {@link PackageManager#GET_SERVICES}
     *  <li> {@link PackageManager#GET_PROVIDERS}
     * </ul>
     */
    public int type = PackageManager.GET_ACTIVITIES;


    public static final int APP_TYPE_USER = 1;
    public static final int APP_TYPE_SYSTEM = 2;
    public int appType = APP_TYPE_USER;


    public static final int PROTECTION_WORLD_ACCESSIBLE = 1;
    public static final int PROTECTION_NORMAL = 2;
    public static final int PROTECTION_DANGEROUS = 4;
    public static final int PROTECTION_SIGNATURE = 8;
    public static final int PROTECTION_SYSTEM = 16;
    public static final int PROTECTION_DEVELOPMENT = 32;
    public static final int PROTECTION_UNEXPORTED = 64;
    public static final int PROTECTION_UNKNOWN = 128;

    public int protection = PROTECTION_WORLD_ACCESSIBLE;

    public static final int PROTECTION_ANY = 128 * 2 - 1;

    public static final int PROTECTION_ANY_OBTAINABLE =
            PROTECTION_WORLD_ACCESSIBLE |
            PROTECTION_NORMAL |
            PROTECTION_DANGEROUS |
            PROTECTION_DEVELOPMENT;

    public static final int PROTECTION_ANY_EXPORTED =
            PROTECTION_ANY & ~PROTECTION_UNEXPORTED;

    static final int PROTECTION_ANY_LEVEL =
            ComponentFetcher.PROTECTION_NORMAL |
            ComponentFetcher.PROTECTION_DANGEROUS |
            ComponentFetcher.PROTECTION_SIGNATURE |
            ComponentFetcher.PROTECTION_SYSTEM |
            ComponentFetcher.PROTECTION_DEVELOPMENT;

    /**
     * Preset protection filters for displaying in Spinner in dialog
     */
    private static final int[] PROTECTION_PRESETS = new int[] {
            PROTECTION_ANY,
            PROTECTION_ANY_EXPORTED,
            PROTECTION_ANY_OBTAINABLE,
            PROTECTION_WORLD_ACCESSIBLE
    };

    /**
     * Preset protection filters for displaying in Spinner in dialog
     */
    private static final int[] PROTECTION_PRESETS_MENU_IDS = new int[] {
            R.id.permission_filter_all,
            R.id.permission_filter_exported,
            R.id.permission_filter_obtainable,
            R.id.permission_filter_world_accessible
    };

    /**
     * This is used for checking if we can skip looking up PermissionInfo because filtering result will be the same
     * no matter what protectionLevel is set
     */
    private static final int PROTECTION_ANY_PERMISSION =
            PROTECTION_ANY &~ (PROTECTION_WORLD_ACCESSIBLE | PROTECTION_UNEXPORTED);



    public String requireMetaDataSubstring = null;

    public boolean testWritePermissionForProviders = false;

    public boolean includeOnlyProvidersAllowingPermissionGranting = false;

    public ComponentFetcher() {}

    // Fetching
    @Override
    Promise<Object, Throwable, Void> getEntriesAsync(Context context) {
        MyPackageManager myPackageManager = MyPackageManagerImpl.getInstance(context);

        Promise<Collection<MyPackageInfo>, Void, Void> packagesPromise = myPackageManager.getPackages(false);
        Promise<Map<String, MyPermissionInfo>, Void, Void> permissionsPromise = myPackageManager.getPermissions();

        DefaultDeferredManager dm = new DefaultDeferredManager();
        final PackageManager pm = context.getPackageManager();
        return dm
                .when(packagesPromise, permissionsPromise)
                .then(new DoneFilter<MultipleResults, Object>() {
                    @Override
                    public Object filterDone(MultipleResults result) {
                        @SuppressWarnings("unchecked")
                        Collection<MyPackageInfo> packages = (Collection<MyPackageInfo>) result.get(0).getResult();
                        @SuppressWarnings("unchecked")
                        Map<String, MyPermissionInfo> permissions = (Map<String, MyPermissionInfo>) result.get(1).getResult();

                        ArrayList<Category> selectedApps = new ArrayList<Category>();

                        //
                        for (MyPackageInfo pack : packages) {
                            // System app filter
                            if (((
                                    pack.isSystemApplication() ?
                                            APP_TYPE_SYSTEM :
                                            APP_TYPE_USER)
                                    & appType) == 0) {
                                continue;
                            }

                            // Scan components
                            ArrayList<Component> selectedComponents = new ArrayList<Component>();

                            if ((type & PackageManager.GET_ACTIVITIES) != 0) {
                                scanComponents(permissions, pack.getActivities(), selectedComponents, false);
                            }
                            if ((type & PackageManager.GET_RECEIVERS) != 0) {
                                scanComponents(permissions, pack.getReceivers(), selectedComponents, false);
                            }
                            if ((type & PackageManager.GET_SERVICES) != 0) {
                                scanComponents(permissions, pack.getServices(), selectedComponents, false);
                            }
                            if ((type & PackageManager.GET_PROVIDERS) != 0) {
                                scanComponents(permissions, pack.getProviders(), selectedComponents, testWritePermissionForProviders);
                            }

                            // Check if we filtered out all components and skip whole app if so
                            if (selectedComponents.isEmpty()) {
                                continue;
                            }

                            // Build and add app descriptor
                            Category app = new Category();
                            app.title = String.valueOf(pack.loadLabel(pm));
                            app.subtitle = pack.getPackageName();
                            app.components = selectedComponents.toArray(new Component[selectedComponents.size()]);
                            selectedApps.add(app);
                        }
                        Category[] selectedAppsArray = selectedApps.toArray(new Category[selectedApps.size()]);
                        Arrays.sort(selectedAppsArray, new Comparator<Category>() {
                            @Override
                            public int compare(Category lhs, Category rhs) {
                                return lhs.subtitle.compareTo(rhs.subtitle);
                            }
                        });
                        return selectedAppsArray;
                    }
                });
    }

    private void scanComponents(Map<String, MyPermissionInfo> pm, MyComponentInfo[] components, ArrayList<Component> outList, boolean checkWritePermission) {
        // Scan components
        for (MyComponentInfo cmp : components) {
            if (!checkMetaDataFilter(cmp.getMetaData())) {
                continue;
            }
            if (!checkPermissionFilter(pm, cmp, checkWritePermission)) {
                continue;
            }
            if (includeOnlyProvidersAllowingPermissionGranting && cmp.getType() == IntentEditorConstants.PROVIDER) {
                ProviderInfo providerInfo = cmp.getProviderInfo();
                if (!providerInfo.grantUriPermissions) {
                    continue;
                }
            }
            Component component = new Component();
            String name = cmp.getName();
            String packageName = cmp.getOwnerPackage().getPackageName();
            component.title = name.startsWith(packageName) ? name.substring(packageName.length()) : name;
            component.componentInfo = cmp;
            outList.add(component);
        }
    }

    private boolean checkMetaDataFilter(Bundle metaData) {
        if (requireMetaDataSubstring == null) {
            return true;
        }
        if (metaData == null || metaData.isEmpty()) {
            return false;
        }
        if (requireMetaDataSubstring.length() == 0) {
            return true;
        }
        for (String key : metaData.keySet()) {
            if (key.contains(requireMetaDataSubstring)) {
                return true;
            }
        }
        return false;
    }

    private boolean checkPermissionFilter(Map<String, MyPermissionInfo> permissionMap, MyComponentInfo cmp, boolean checkWritePermission) {
        // Not exported?
        if (!cmp.isExported()) {
            return (protection & PROTECTION_UNEXPORTED) != 0;
        }

        // Get checked permission
        String permission =
                checkWritePermission ?
                        cmp.getWritePermission() :
                        cmp.getPermission();

        // World accessible
        if (permission == null) {
            return (protection & PROTECTION_WORLD_ACCESSIBLE) != 0;
        }

        // Skip checking protectionLevel if it doesn't matter
        if ((protection & PROTECTION_ANY_PERMISSION) == PROTECTION_ANY_PERMISSION) {
            return true;
        }
        if ((protection & PROTECTION_ANY_PERMISSION) == 0) {
            return false;
        }

        // Obtain PermissionInfo
        MyPermissionInfo permissionInfo = permissionMap.get(permission);
        if (permissionInfo == null) {
            return (protection & PROTECTION_UNKNOWN) != 0;
        }

        return checkProtectionLevel(permissionInfo, protection);
    }

    @SuppressLint("InlinedApi")
    static boolean checkProtectionLevel(MyPermissionInfo permissionInfo, int protectionFilter) {
        // Skip test if all options are checked
        if ((protectionFilter & PROTECTION_ANY_LEVEL) == PROTECTION_ANY_LEVEL) {
            return true;
        }

        // Match against our flags
        return ((
                permissionInfo.isNormal() ? PROTECTION_NORMAL :
                permissionInfo.isDangerous() ? PROTECTION_DANGEROUS :
                (
                    (permissionInfo.isSignature()
                        ? PROTECTION_SIGNATURE : 0) |
                    (permissionInfo.isSystem()
                        ? PROTECTION_SYSTEM : 0) |
                    (permissionInfo.isDevelopment()
                        ? PROTECTION_DEVELOPMENT : 0)
                )
        ) & protectionFilter) != 0;
    }

    @SuppressLint("InlinedApi")
    // TODO: drop this method once we migrate PermissionFetcher to MyPackageManager
    static boolean checkProtectionLevelRaw(PermissionInfo permissionInfo, int protectionFilter) {
        // Skip test if all options are checked
        if ((protectionFilter & PROTECTION_ANY_LEVEL) == PROTECTION_ANY_LEVEL) {
            return true;
        }

        // Test protectionLevel
        int protectionLevel = permissionInfo.protectionLevel;
        if (protectionLevel == PermissionInfo.PROTECTION_SIGNATURE_OR_SYSTEM) {
            protectionLevel = PermissionInfo.PROTECTION_SIGNATURE | PermissionInfo.PROTECTION_FLAG_SYSTEM;
        }
        int protectionLevelBase = protectionLevel & PermissionInfo.PROTECTION_MASK_BASE;
        int protectionLevelFlags = protectionLevel & PermissionInfo.PROTECTION_MASK_FLAGS;

        // Match against our flags
        return ((
                protectionLevel == PermissionInfo.PROTECTION_NORMAL ? PROTECTION_NORMAL :
                protectionLevel == PermissionInfo.PROTECTION_DANGEROUS ? PROTECTION_DANGEROUS :
                (
                    ((protectionLevelBase == PermissionInfo.PROTECTION_SIGNATURE)
                        ? PROTECTION_SIGNATURE : 0) |
                        (((protectionLevelFlags & PermissionInfo.PROTECTION_FLAG_SYSTEM) != 0)
                                ? PROTECTION_SYSTEM : 0) |
                        (((protectionLevelFlags & PermissionInfo.PROTECTION_FLAG_DEVELOPMENT) != 0)
                                ? PROTECTION_DEVELOPMENT : 0)
                )
        ) & protectionFilter) != 0;
    }

    // Configuration UI
    @Override
    int getConfigurationLayout() {
        return R.layout.components_filter;
    }

    @Override
    void initConfigurationForm(final FetcherOptionsDialog dialog) {
        // Disable development permission checkbox if it's not available
        if (!DEVELOPMENT_PERMISSIONS_SUPPORTED) {
            dialog.findView(R.id.permission_filter_development).setEnabled(false);
        }

        // Prepare protection preset spinner
        {
            // Find current preset
            int currentPresetId = PROTECTION_PRESETS.length; // "Custom" if nothing found
            if (!testWritePermissionForProviders) {
                for (int i = 0; i < PROTECTION_PRESETS.length; i++) {
                    if (protection == PROTECTION_PRESETS[i]) {
                        currentPresetId = i;
                        dialog.findView(R.id.permission_filter_details).setVisibility(View.GONE);
                        break;
                    }
                }
            }

            // Fill spinner
            Spinner protectionPresetSpinner = (Spinner) dialog.findView(R.id.permission_filter_spinner);
            Activity activity = dialog.getActivity();
            protectionPresetSpinner.setAdapter(new ArrayAdapter<String>(activity, android.R.layout.simple_spinner_item,
                    new String[]{
                            activity.getString(R.string.permission_filter_show_all), // 0
                            activity.getString(R.string.permission_filter_show_exported), // 1
                            activity.getString(R.string.permission_filter_show_obtainable), // 2
                            activity.getString(R.string.permission_filter_world_accessible), // 3
                            activity.getString(R.string.filter_custom) // 4
                    }
            ));
            protectionPresetSpinner.setSelection(currentPresetId);

            // Set up spinner event
            protectionPresetSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
                @Override
                public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                    boolean isCustom = position == PROTECTION_PRESETS.length;
                    if (!isCustom) {
                        int preset = PROTECTION_PRESETS[position];
                        dialog.setBoxChecked(R.id.permission_filter_world_accessible, (preset & PROTECTION_WORLD_ACCESSIBLE) != 0);
                        dialog.setBoxChecked(R.id.permission_filter_normal, (preset & PROTECTION_NORMAL) != 0);
                        dialog.setBoxChecked(R.id.permission_filter_dangerous, (preset & PROTECTION_DANGEROUS) != 0);
                        dialog.setBoxChecked(R.id.permission_filter_signature, (preset & PROTECTION_SIGNATURE) != 0);
                        dialog.setBoxChecked(R.id.permission_filter_system, (preset & PROTECTION_SYSTEM) != 0);
                        dialog.setBoxChecked(R.id.permission_filter_development, (preset & PROTECTION_DEVELOPMENT) != 0);
                        dialog.setBoxChecked(R.id.permission_filter_unexported, (preset & PROTECTION_UNEXPORTED) != 0);
                        dialog.setBoxChecked(R.id.permission_filter_unknown, (preset & PROTECTION_UNKNOWN) != 0);
                        dialog.setBoxChecked(R.id.read_permission, true);
                    }
                    dialog.findView(R.id.permission_filter_details).setVisibility(isCustom ? View.VISIBLE : View.GONE);
                }

                @Override
                public void onNothingSelected(AdapterView<?> parent) {
                    // Spinner cannot have nothing selected
                }
            });
        }



        // Fill form
        dialog.setBoxChecked(R.id.system_apps, (appType & APP_TYPE_SYSTEM) != 0);
        dialog.setBoxChecked(R.id.user_apps, (appType & APP_TYPE_USER) != 0);

        dialog.setBoxChecked(R.id.activities, (type & PackageManager.GET_ACTIVITIES) != 0);
        dialog.setBoxChecked(R.id.receivers, (type & PackageManager.GET_RECEIVERS) != 0);
        dialog.setBoxChecked(R.id.services, (type & PackageManager.GET_SERVICES) != 0);
        dialog.setBoxChecked(R.id.content_providers, (type & PackageManager.GET_PROVIDERS) != 0);

        dialog.setBoxChecked(R.id.permission_filter_world_accessible, (protection & PROTECTION_WORLD_ACCESSIBLE) != 0);
        dialog.setBoxChecked(R.id.permission_filter_normal, (protection & PROTECTION_NORMAL) != 0);
        dialog.setBoxChecked(R.id.permission_filter_dangerous, (protection & PROTECTION_DANGEROUS) != 0);
        dialog.setBoxChecked(R.id.permission_filter_signature, (protection & PROTECTION_SIGNATURE) != 0);
        dialog.setBoxChecked(R.id.permission_filter_system, (protection & PROTECTION_SYSTEM) != 0);
        dialog.setBoxChecked(R.id.permission_filter_development, (protection & PROTECTION_DEVELOPMENT) != 0);
        dialog.setBoxChecked(R.id.permission_filter_unexported, (protection & PROTECTION_UNEXPORTED) != 0);
        dialog.setBoxChecked(R.id.permission_filter_unknown, (protection & PROTECTION_UNKNOWN) != 0);

        dialog.setBoxChecked(testWritePermissionForProviders ? R.id.write_permission : R.id.read_permission, true);
        dialog.setBoxChecked(R.id.only_providers_with_grant_uri_permission, includeOnlyProvidersAllowingPermissionGranting);

        dialog.setBoxChecked(R.id.metadata, requireMetaDataSubstring != null);
        dialog.setTextInField(R.id.metadata_substring, requireMetaDataSubstring);

        // Set up sections showing when their checkboxes are checked
        dialog.findView(R.id.content_provider_permission_type).setVisibility(testWritePermissionForProviders ? View.VISIBLE : View.GONE);
        dialog.findView(R.id.content_provider_options).setVisibility((type & PackageManager.GET_PROVIDERS) != 0 ? View.VISIBLE : View.GONE);
        ((CheckBox) dialog.findView(R.id.content_providers)).setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                dialog.findView(R.id.content_provider_permission_type).setVisibility(isChecked ? View.VISIBLE : View.GONE);
                dialog.findView(R.id.content_provider_options).setVisibility(isChecked ? View.VISIBLE : View.GONE);
                if (!isChecked) {
                    dialog.setBoxChecked(R.id.read_permission, true);
                    dialog.setBoxChecked(R.id.only_providers_with_grant_uri_permission, false);
                }
            }
        });

        dialog.findView(R.id.metadata_details).setVisibility(requireMetaDataSubstring != null ? View.VISIBLE : View.GONE);
        ((CheckBox) dialog.findView(R.id.metadata)).setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                dialog.findView(R.id.metadata_details).setVisibility(isChecked ? View.VISIBLE : View.GONE);
                if (!isChecked) {
                    dialog.setTextInField(R.id.metadata_substring, "");
                }
            }
        });
    }

    @Override
    void updateFromConfigurationForm(FetcherOptionsDialog dialog) {
        appType =
                (dialog.isBoxChecked(R.id.system_apps) ? APP_TYPE_SYSTEM : 0) |
                (dialog.isBoxChecked(R.id.user_apps) ? APP_TYPE_USER : 0);

        type =
                (dialog.isBoxChecked(R.id.activities) ? PackageManager.GET_ACTIVITIES : 0) |
                (dialog.isBoxChecked(R.id.receivers) ? PackageManager.GET_RECEIVERS : 0) |
                (dialog.isBoxChecked(R.id.services) ? PackageManager.GET_SERVICES : 0) |
                (dialog.isBoxChecked(R.id.content_providers) ? PackageManager.GET_PROVIDERS : 0);

        protection =
                (dialog.isBoxChecked(R.id.permission_filter_world_accessible) ? PROTECTION_WORLD_ACCESSIBLE : 0) |
                (dialog.isBoxChecked(R.id.permission_filter_normal) ? PROTECTION_NORMAL : 0) |
                (dialog.isBoxChecked(R.id.permission_filter_dangerous) ? PROTECTION_DANGEROUS : 0) |
                (dialog.isBoxChecked(R.id.permission_filter_signature) ? PROTECTION_SIGNATURE : 0) |
                (dialog.isBoxChecked(R.id.permission_filter_system) ? PROTECTION_SYSTEM : 0) |
                (dialog.isBoxChecked(R.id.permission_filter_development) ? PROTECTION_DEVELOPMENT : 0) |
                (dialog.isBoxChecked(R.id.permission_filter_unexported) ? PROTECTION_UNEXPORTED : 0) |
                (dialog.isBoxChecked(R.id.permission_filter_unknown) ? PROTECTION_UNKNOWN : 0);

        includeOnlyProvidersAllowingPermissionGranting = dialog.isBoxChecked(R.id.only_providers_with_grant_uri_permission);

        boolean requireMetaData = dialog.isBoxChecked(R.id.metadata);
        requireMetaDataSubstring =
                requireMetaData ?
                dialog.getTextFromField(R.id.metadata_substring) :
                null;

        testWritePermissionForProviders = dialog.isBoxChecked(R.id.write_permission);
    }



    // Verification
    @Override
    boolean isExcludingEverything() {
        return
                appType == 0 ||
                type == 0 ||
                protection == 0;
    }

    //
    // Parcelable
    //
    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeInt(
                (testWritePermissionForProviders ? 2 : 0)
        );
        dest.writeInt(type);
        dest.writeInt(appType);
        dest.writeInt(protection);
        dest.writeString(requireMetaDataSubstring);
    }

    public static final Creator<ComponentFetcher> CREATOR = new Creator<ComponentFetcher>() {
        @Override
        public ComponentFetcher createFromParcel(Parcel source) {
            int flags = source.readInt();
            ComponentFetcher fetcher = new ComponentFetcher();
            fetcher.type = source.readInt();
            fetcher.appType = source.readInt();
            fetcher.protection = source.readInt();
            fetcher.requireMetaDataSubstring = source.readString();
            fetcher.testWritePermissionForProviders = (flags & 2) != 0;
            return fetcher;
        }

        @Override
        public ComponentFetcher[] newArray(int size) {
            return new ComponentFetcher[size];
        }
    };

    // Options menu
    @Override
    void onPrepareOptionsMenu(Menu menu) {
        if (appType == APP_TYPE_USER) {
            menu.findItem(R.id.system_apps).setVisible(true);
        } else if (appType == APP_TYPE_SYSTEM) {
            menu.findItem(R.id.user_apps).setVisible(true);
        }

        if (type == PackageManager.GET_ACTIVITIES) {
            menu.findItem(R.id.activities).setChecked(true);
        } else if (type == PackageManager.GET_RECEIVERS) {
            menu.findItem(R.id.broadcasts).setChecked(true);
        } else if (type == PackageManager.GET_SERVICES) {
            menu.findItem(R.id.services).setChecked(true);
        } else if (type == PackageManager.GET_PROVIDERS) {
            menu.findItem(R.id.content_providers).setChecked(true);
        }

        menu.findItem(R.id.simple_filter_permission).setVisible(true);
        for (int i = 0; i < PROTECTION_PRESETS_MENU_IDS.length; i++) {
            if (protection == PROTECTION_PRESETS[i]) {
                menu.findItem(PROTECTION_PRESETS_MENU_IDS[i]).setChecked(true);
            }
        }
    }

    @Override
    boolean onOptionsItemSelected(int id) {
        switch (id) {
            case R.id.system_apps: appType = APP_TYPE_SYSTEM; return true;
            case R.id.user_apps:   appType = APP_TYPE_USER;   return true;

            case R.id.activities:        type = PackageManager.GET_ACTIVITIES; return true;
            case R.id.broadcasts:        type = PackageManager.GET_RECEIVERS;  return true;
            case R.id.services:          type = PackageManager.GET_SERVICES;   return true;
            case R.id.content_providers: type = PackageManager.GET_PROVIDERS;  return true;

            case R.id.permission_filter_all:              protection = PROTECTION_ANY;              return true;
            case R.id.permission_filter_exported:         protection = PROTECTION_ANY_EXPORTED;     return true;
            case R.id.permission_filter_obtainable:       protection = PROTECTION_ANY_OBTAINABLE;   return true;
            case R.id.permission_filter_world_accessible: protection = PROTECTION_WORLD_ACCESSIBLE; return true;
        }
        return false;
    }

    // JSON serialization & name
    static final Descriptor DESCRIPTOR = new Descriptor(ComponentFetcher.class, "components", R.string.components) {
        @Override
        Fetcher unserializeFromJSON(JSONObject jsonObject) throws JSONException {
            ComponentFetcher fetcher = new ComponentFetcher();
            fetcher.type = jsonObject.getInt("componentType");
            fetcher.appType = jsonObject.getInt("appType");
            fetcher.protection = jsonObject.getInt("protectionFilter");
            if ((fetcher.type & PackageManager.GET_PROVIDERS) != 0) {
                fetcher.testWritePermissionForProviders = jsonObject.getBoolean("testProviderWrite");
            }
            fetcher.requireMetaDataSubstring = jsonObject.getString("metadataSubstring");
            return fetcher;
        }
    };

    @Override
    JSONObject serializeToJSON() throws JSONException {
        JSONObject jsonObject = new JSONObject();
        jsonObject.put("componentType", type);
        jsonObject.put("appType", appType);
        jsonObject.put("protectionFilter", protection);
        if ((type & PackageManager.GET_PROVIDERS) != 0) {
            jsonObject.put("testProviderWrite", testWritePermissionForProviders);
        }
        jsonObject.put("metadataSubstring", requireMetaDataSubstring);
        return jsonObject;
    }
}
