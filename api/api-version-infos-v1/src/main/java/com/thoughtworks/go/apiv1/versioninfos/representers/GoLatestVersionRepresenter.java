/*
 * Copyright Thoughtworks, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.thoughtworks.go.apiv1.versioninfos.representers;

import com.thoughtworks.go.api.representers.JsonReader;
import com.thoughtworks.go.apiv1.versioninfos.models.GoLatestVersion;

public class GoLatestVersionRepresenter {
    public static GoLatestVersion fromJSON(JsonReader jsonReader) {
        return new GoLatestVersion()
                .setMessage(jsonReader.getString("message"))
                .setMessageSignature(jsonReader.getString("message_signature"))
                .setSigningPublicKey(jsonReader.getString("signing_public_key"))
                .setSigningPublicKeySignature(jsonReader.getString("signing_public_key_signature"));
    }
}
