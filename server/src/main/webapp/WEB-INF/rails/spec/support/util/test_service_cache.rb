#
# Copyright Thoughtworks, Inc.
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#     http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
#

class TestServiceCache
  @@services = {}

  def self.get_service alias_name, service
    @@services[alias_name] || Spring.bean(service)
  end

  def self.replace_service alias_name, service
    @@services[alias_name] = service
  end

  def self.clear_services
    @@services = {}
  end
end