/**
 * 客户端 SEO：更新 title / description / keywords / Open Graph
 */
export function upsertMeta(attr, key, content) {
  if (!content) return
  let el = document.head.querySelector(`meta[${attr}="${key}"]`)
  if (!el) {
    el = document.createElement('meta')
    el.setAttribute(attr, key)
    document.head.appendChild(el)
  }
  el.setAttribute('content', content)
}

export function upsertLink(rel, href) {
  if (!href) return
  let el = document.head.querySelector(`link[rel="${rel}"]`)
  if (!el) {
    el = document.createElement('link')
    el.setAttribute('rel', rel)
    document.head.appendChild(el)
  }
  el.setAttribute('href', href)
}

export function applySeo(meta = {}) {
  const {
    title,
    description,
    keywords,
    ogTitle,
    ogDescription,
    ogType = 'website',
    robots = 'index,follow',
  } = meta

  if (title) {
    document.title = title
  }

  upsertMeta('name', 'description', description)
  upsertMeta('name', 'keywords', keywords)
  upsertMeta('name', 'robots', robots)
  upsertMeta('name', 'author', 'AI Agent')

  upsertMeta('property', 'og:title', ogTitle || title)
  upsertMeta('property', 'og:description', ogDescription || description)
  upsertMeta('property', 'og:type', ogType)
  upsertMeta('property', 'og:locale', 'zh_CN')

  upsertMeta('name', 'twitter:card', 'summary')
  upsertMeta('name', 'twitter:title', ogTitle || title)
  upsertMeta('name', 'twitter:description', ogDescription || description)

  // canonical
  try {
    upsertLink('canonical', window.location.href.split('#')[0])
  } catch {
    /* ignore */
  }
}

export function injectJsonLd(data, id = 'seo-jsonld') {
  let el = document.getElementById(id)
  if (!el) {
    el = document.createElement('script')
    el.type = 'application/ld+json'
    el.id = id
    document.head.appendChild(el)
  }
  el.textContent = JSON.stringify(data)
}
