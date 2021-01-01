/*
 * Copyright 2021 ThoughtWorks, Inc.
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
package com.thoughtworks.go.apiv1.pipelineinstance.representers

import com.thoughtworks.go.api.util.GsonTransformer
import com.thoughtworks.go.helper.ModificationsMother
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.Test

import static com.thoughtworks.go.api.base.JsonOutputWriter.jsonDate
import static com.thoughtworks.go.api.base.JsonUtils.toObjectString
import static net.javacrumbs.jsonunit.fluent.JsonFluentAssert.assertThatJson

class MaterialRevisionRepresenterTest {
  @Test
  void 'should render material revision with modifications'() {
    def materialRevision = ModificationsMother.createHgMaterialRevisions().getMaterialRevision(0)

    def expectedJSON = [
      "changed"      : false,
      "material"     : [
        "name"       : "hg-url",
        "fingerprint": "4290e91721d0a0be34955725cfd754113588d9c27c39f9bd1a97c15e55832515",
        "type"       : "Mercurial",
        "description": "URL: hg-url"
      ],
      "modifications": [
        [
          "revision"     : "9fdcf27f16eadc362733328dd481d8a2c29915e1",
          "modified_time": materialRevision.getModification(0).modifiedTime.getTime(),
          "user_name"    : "user2",
          "comment"      : "comment2",
          "email_address": "email2"
        ],
        [
          "revision"     : "eef77acd79809fc14ed82b79a312648d4a2801c6",
          "modified_time": materialRevision.getModification(1).modifiedTime.getTime(),
          "user_name"    : "user1",
          "comment"      : "comment1",
          "email_address": "email1"
        ]
      ]
    ]

    def actualJson = toObjectString({ MaterialRevisionRepresenter.toJSON(it, materialRevision) })

    assertThatJson(actualJson).isEqualTo(expectedJSON)
  }
}
