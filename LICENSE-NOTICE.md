# License notice

Copyright (C) 2026 Gerald Summers.

Unless otherwise noted, the original code in this repository is free software: you can redistribute it and/or modify it under the terms of the GNU Affero General Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version.

This software is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See [LICENSE](LICENSE) for the full license text.

Third-party dependencies, assets, generated files, and vendored material remain under their respective licenses and notices.

The local Valkyrien Skies 2.4.11 `bc-interface.3` override in `mods/` is derived from the pinned upstream artifact (SHA-256 `f99f24de62015451a047f90484cf9d25970cac350105b581c22b2d5247ebfd53`). Its only changed archive member is `valkyrienskies-common.mixins.json`: the client-only Chain Conveyor mixin is moved to the client list, and the vibration ticker mixin is transferred to the paired Better Compat Fixes bridge. The resulting JAR has SHA-256 `97e1c1a0dc9cc20c3506fc3e1edf796fd9b21f224135ee7c38be7f8d63e0e946`; the upstream license and notice members remain in the JAR. The exact local derivation receipt and original manifest are retained under `generated/vibration-override-20261027/`. The recipe receipt is not a publication grant; review all bundled component terms before distributing this locally prepared candidate beyond this workspace.
