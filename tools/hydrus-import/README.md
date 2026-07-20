# Hydrus models import

Fetches http://hydrus.pl/modele.php, normalizes rows into SQLite, and downloads thumbnail images.

```bash
python3 import_models.py --out ../out   # default: ./out
# or from android-client root:
make import-models
```

Outputs:

- `out/models.db`
- `out/images/*.jpg`
