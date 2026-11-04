import { readdirSync, readFileSync, statSync, writeFileSync } from 'node:fs'
import { extname, join } from 'node:path'
import { brotliCompressSync, constants, gzipSync } from 'node:zlib'

const DIST = 'dist'
const COMPRESSIBLE = new Set(['.html', '.js', '.css', '.svg', '.json', '.txt'])
const summaries = new Map()
let files = 0

function walk(dir) {
  for (const name of readdirSync(dir)) {
    const full = join(dir, name)
    const st = statSync(full)
    if (st.isDirectory()) {
      walk(full)
      continue
    }
    const ext = extname(full)
    if (!COMPRESSIBLE.has(ext) || st.size < 256) continue
    const source = readFileSync(full)
    const gzip = gzipSync(source, { level: 9 })
    const brotli = brotliCompressSync(source, {
      params: { [constants.BROTLI_PARAM_QUALITY]: 11 },
    })
    writeFileSync(`${full}.gz`, gzip)
    writeFileSync(`${full}.br`, brotli)

    const entry = summaries.get(ext) ?? { count: 0, raw: 0, gzip: 0, brotli: 0 }
    entry.count++
    entry.raw += source.length
    entry.gzip += gzip.length
    entry.brotli += brotli.length
    summaries.set(ext, entry)
    files++
  }
}

walk(DIST)
console.log(`precompressed ${files} files`)
for (const [ext, entry] of [...summaries].sort()) {
  const kb = (n) => (n / 1024).toFixed(1)
  console.log(
    `  ${ext.padEnd(6)} ${String(entry.count).padStart(3)} files  raw ${kb(entry.raw)} KB  gzip ${kb(entry.gzip)} KB  brotli ${kb(entry.brotli)} KB`,
  )
}
