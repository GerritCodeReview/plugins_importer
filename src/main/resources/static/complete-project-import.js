// Copyright (C) 2015 The Android Open Source Project
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

Gerrit.install(function(self) {
    function onCompleteProjectImport(c) {
      var b = c.button('Complete',
        {onclick: function(){
          c.call(
            {},
            function(r) {
              c.hide();
              window.alert('Import for project "'
                + c.project
                + '" was completed."'),
              Gerrit.go('/admin/projects/' + c.project);
            });
        }});
      c.popup(c.div(
        c.msg('Complete import for project "'
          + c.project
          + '" ?'),
        c.br(),
        c.br(),
        b));
    }
    self.onAction('project', 'delete', onCompleteProjectImport);
  });
