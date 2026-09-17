# X88 Autonomy Build Controller

Local build controller for the Android project. It uses a content hash cache, Gradle build/test gates, persistent state checkpoints, optional Android bridge observation, and an optional repair endpoint.

## Run

```bash
python tools/x88/x88_build_controller.py --repo . --force --no-repair
```

On Termux, install Python, Git, JDK 17, and Gradle first. The controller never treats an unavailable AI repair provider as a successful repair. External publication, irreversible changes, and Android system confirmations stay outside the autonomous loop.

## Test

```bash
cd tools/x88
python -m unittest -v test_x88_build_controller.py
```
