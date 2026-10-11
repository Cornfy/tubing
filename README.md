# Tubing

Third-party Android client based on SFA, with native sub-tools template support.

Project and issue reports: https://github.com/Cornfy/tubing

Upstream SFA: https://github.com/SagerNet/sing-box-for-android

Templates retain their source separately from generated configurations. Empty subscriptions use a direct placeholder; fetch failures retain the last working configuration. Template sources can be exported from the profile editor and imported again.

The `feat/template-profile` branch carries individual patches on the SFA 1.14.3 stable commit. `release/template` squashes those changes for release builds. Manually dispatch the workflow with `publish=false` to validate a feature branch without publishing a release. Release-branch pushes build all Android ABIs and publish APKs plus Tubing update metadata.

The workflow pins the compatible core revision in `CORE_REF`; update it when integrating new core features. Application ID is currently unchanged. Back up/export configurations before any future application ID migration.

## Documentation

https://sing-box.sagernet.org/installation/clients/sfa/

## License

```
Copyright (C) 2022 by nekohasekai <contact-sagernet@sekai.icu>

This program is free software: you can redistribute it and/or modify
it under the terms of the GNU General Public License as published by
the Free Software Foundation, either version 3 of the License, or
(at your option) any later version.

This program is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU General Public License for more details.

You should have received a copy of the GNU General Public License
along with this program. If not, see <http://www.gnu.org/licenses/>.

In addition, no derivative work may use the name or imply association
with this application without prior consent.
```

Under the license, that forks of the app are not allowed to be listed on F-Droid or other app stores
under the original name.
