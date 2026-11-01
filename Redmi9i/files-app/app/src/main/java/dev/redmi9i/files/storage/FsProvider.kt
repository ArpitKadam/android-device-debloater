package dev.redmi9i.files.storage

import java.io.InputStream
import java.io.OutputStream

/** One storage backend. All paths in [FileRef] are absolute and '/'-separated. */
interface FsProvider {
    val id: String

    suspend fun list(dir: FileRef): List<FileInfo>
    suspend fun stat(ref: FileRef): FileInfo?
    fun openRead(ref: FileRef): InputStream
    fun openWrite(ref: FileRef): OutputStream
    suspend fun mkdir(parent: FileRef, name: String): FileRef
    suspend fun createFile(parent: FileRef, name: String): FileRef
    suspend fun delete(ref: FileRef)
    suspend fun rename(ref: FileRef, newName: String): FileRef
    fun child(dir: FileRef, name: String): FileRef

    /** True when [fastMove] can relocate [from] into [toDir] without copying bytes. */
    fun supportsFastMove(from: FileRef, toDir: FileRef): Boolean
    suspend fun fastMove(from: FileRef, toDir: FileRef): FileRef
}
