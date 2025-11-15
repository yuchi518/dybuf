import { accessSync, constants, readdirSync } from 'node:fs';
import { join } from 'node:path';

const FIXTURE_DIR = join(process.cwd(), '..', 'fixtures', 'v1');

try {
  accessSync(FIXTURE_DIR, constants.R_OK);
  const entries = readdirSync(FIXTURE_DIR);
  if (!entries.some((file) => file.endsWith('.json'))) {
    throw new Error('fixtures/v1 is present but contains no JSON test vectors');
  }
} catch (err) {
  console.error('Shared fixtures are missing. Run `tools/generate_fixtures.sh` from the repo root before testing.');
  if (err instanceof Error) {
    console.error(`Details: ${err.message}`);
  }
  process.exit(1);
}
