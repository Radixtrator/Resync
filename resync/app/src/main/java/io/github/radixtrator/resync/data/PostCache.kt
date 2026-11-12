package io.github.radixtrator.resync.data

import android.util.LruCache

/**
 * Posts the app has already shown in a listing, by id, so a comment page can draw its post the moment it opens
 * instead of waiting for the whole thread. The comment page's own copy replaces it once that arrives.
 */
object PostCache {
    private val posts = LruCache<String, Post>(500)

    operator fun get(id: String): Post? = posts[id]

    fun put(post: Post) {
        posts.put(post.id, post)
    }

    fun putAll(list: Iterable<Post>) = list.forEach(::put)
}
