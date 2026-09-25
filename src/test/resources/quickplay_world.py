"""Launch the pinned Forge client directly into an isolated singleplayer save."""

import shlex
import sys
from pathlib import Path

from portablemc.forge import ForgeVersion
from portablemc.standard import Context


main_dir, work_dir, java, jvm_args, username, uuid, world = sys.argv[1:]
version = ForgeVersion("1.20.1-47.4.13", context=Context(Path(main_dir), Path(work_dir)))
version.jvm_path = Path(java)
version.resolution = (1280, 720)
version.set_auth_offline(username, uuid)
version.set_quick_play_singleplayer(world)
environment = version.install()
environment.jvm_args.extend(shlex.split(jvm_args))
environment.run()
