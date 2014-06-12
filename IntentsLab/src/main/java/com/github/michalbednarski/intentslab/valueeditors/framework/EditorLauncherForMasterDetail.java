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

package com.github.michalbednarski.intentslab.valueeditors.framework;

import android.os.Bundle;

import com.github.michalbednarski.intentslab.MasterDetailActivity;

/**
 * Variant of EditorLauncher for using editors as detail
 *
 * This will auto-save on switch, so detail must not be used for other stuff
 * TODO: auto-save on switch and add method for explicit save for use on exit
 */
public class EditorLauncherForMasterDetail extends EditorLauncher {
    /**
     * Constructor
     *
     * @param activity
     * @param tag
     */
    public EditorLauncherForMasterDetail(MasterDetailActivity activity, String tag) {
        super(activity, tag);
    }

    @Override
    void openEditorFragment(Class<? extends ValueEditorFragment> editorFragment, Bundle args) {
        MasterDetailActivity activity = (MasterDetailActivity) mFragment.getActivity();
        if (activity.usingTabletView()) {
            activity.openFragment(
                    editorFragment,
                    args
            );
        } else {
            super.openEditorFragment(editorFragment, args);
        }
    }
}