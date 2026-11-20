<script setup lang="ts">
import { ref, watch } from 'vue'
import MarkdownIt from 'markdown-it'
import katex from 'katex'
import 'katex/dist/katex.min.css'
import { getMediaObjectUrl } from '../api/media'

const props = defineProps<{
  content: string
  inline?: boolean
}>()

const html = ref('')
const md = new MarkdownIt({ html: false, linkify: true, breaks: true })
let renderSeq = 0

watch(
  () => props.content,
  () => {
    render()
  },
  { immediate: true }
)

async function render() {
  const seq = ++renderSeq
  let source = props.content || ''

  const mediaIds = Array.from(
    new Set(Array.from(source.matchAll(/media:\/\/([0-9a-fA-F-]{36})/g)).map((match) => match[1]))
  )
  for (const mediaId of mediaIds) {
    let replacement = ''
    try {
      replacement = await getMediaObjectUrl(mediaId)
    } catch {
      replacement = ''
    }
    source = source.split(`media://${mediaId}`).join(replacement)
  }

  const blockMath: string[] = []
  source = source.replace(/\$\$([\s\S]+?)\$\$/g, (_match, expression: string) => {
    const token = `MATHBLOCKTOKEN${blockMath.length}X`
    blockMath.push(renderMath(expression, true))
    return `\n\n${token}\n\n`
  })

  const inlineMath: string[] = []
  source = source.replace(/\$([^$\n]+?)\$/g, (_match, expression: string) => {
    const token = `MATHINLINETOKEN${inlineMath.length}X`
    inlineMath.push(renderMath(expression, false))
    return token
  })

  let output = md.render(source)
  output = output.replace(/<p>MATHBLOCKTOKEN(\d+)X<\/p>/g, (_match, index: string) => {
    return blockMath[Number(index)] ?? ''
  })
  output = output.replace(/MATHINLINETOKEN(\d+)X/g, (_match, index: string) => {
    return inlineMath[Number(index)] ?? ''
  })

  if (seq === renderSeq) {
    html.value = output
  }
}

function renderMath(expression: string, displayMode: boolean) {
  try {
    return katex.renderToString(expression.trim(), {
      displayMode,
      throwOnError: false,
      strict: false
    })
  } catch {
    return expression
  }
}
</script>

<template>
  <div class="markdown-body" :class="{ inline }" v-html="html"></div>
</template>

<style scoped>
.markdown-body { line-height: 1.7; word-break: break-word; }
.markdown-body.inline { display: inline; }
.markdown-body :deep(p) { margin: 0 0 8px; }
.markdown-body :deep(p:last-child) { margin-bottom: 0; }
.markdown-body :deep(img) { max-width: 100%; border-radius: 8px; }
.markdown-body :deep(pre) { padding: 12px; background: #0f172a; color: #e2e8f0; border-radius: 8px; overflow: auto; }
.markdown-body :deep(code) { font-family: 'JetBrains Mono', Consolas, monospace; }
.markdown-body :deep(.katex-display) { margin: 8px 0; overflow-x: auto; overflow-y: hidden; }
</style>
