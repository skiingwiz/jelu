package io.github.bayang.jelu.dao

import io.github.bayang.jelu.dto.*
import io.github.bayang.jelu.utils.nowInstant
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.sql.*
import org.springframework.stereotype.Repository
import java.time.Instant
import java.util.*

@Repository
class BookRepository(
    val readingEventRepository: ReadingEventRepository
) {

    fun findAll(searchTerm: String?): SizedIterable<Book> {
        return if (! searchTerm.isNullOrBlank()) {
            Book.find { BookTable.title like searchTerm }
        } else {
            Book.all()
        }
    }

    fun findAllBooksByUser(user: User): List<UserBook> {
        return UserBook.find { UserBookTable.user eq user.id }
                .orderBy(Pair(UserBookTable.lastReadingEventDate, SortOrder.DESC_NULLS_LAST))
                .toList()
    }

    fun findAllAuthors(): SizedIterable<Author> = Author.all()

    fun findAllTags(): SizedIterable<Tag> = Tag.all()

    fun findAuthorsByName(name: String): List<Author> {
        return Author.find{AuthorTable.name like "%$name%" }.toList()
    }

    fun findTagsByName(name: String): List<Tag> {
        return Tag.find{TagTable.name like "%$name%" }.toList()
    }

    fun findBookById(bookId: UUID): Book = Book[bookId]

    fun findBookByTitle(title: String): SizedIterable<Book> = Book.find { BookTable.title like title }

    fun findAuthorsById(authorId: UUID): Author = Author[authorId]

    fun findTagById(tagId: UUID): Tag = Tag[tagId]

    fun update(updated: Book, book: BookCreateDto): Book {
        if (!book.title.isNullOrBlank()) {
            updated.title = book.title
        }
        book.isbn10.let { updated.isbn10 = it }
        book.isbn13.let { updated.isbn13 = it }
        book.pageCount.let { updated.pageCount = it }
        book.publisher.let { updated.publisher = it }
        book.summary.let { updated.summary = it }
        // image must be set when saving file succeeds
//        book.image.let { updated.image = it }
        book.publishedDate.let { updated.publishedDate = it }
        book.series.let { updated.series = it }
        book.numberInSeries.let { updated.numberInSeries = it }
        book.amazonId.let { updated.amazonId = it }
        book.goodreadsId.let { updated.goodreadsId = it }
        book.googleId.let { updated.googleId = it }
        book.librarythingId.let { updated.librarythingId = it }
        updated.modificationDate = nowInstant()
        val authorsList = mutableListOf<Author>()
        book.authors?.forEach {
            val authorEntity: Author? = findAuthorsByName(it.name).firstOrNull()
            if (authorEntity != null) {
                authorsList.add(authorEntity)
            } else {
                authorsList.add(save(it))
            }
        }
        if (authorsList.isNotEmpty()) {
            if (updated.authors.empty()) {
                updated.authors = SizedCollection(authorsList)
            }
            else {
                val existing = updated.authors.toMutableList()
                existing.addAll(authorsList)
                val merged: SizedCollection<Author> = SizedCollection(existing)
                updated.authors = merged
            }
        }
        val tagsList = mutableListOf<Tag>()
        book.tags?.forEach {
            val tagEntity: Tag? = findTagsByName(it.name).firstOrNull()
            if (tagEntity != null) {
                tagsList.add(tagEntity)
            } else {
                tagsList.add(save(it))
            }
        }
        if (tagsList.isNotEmpty()) {
            if (updated.tags.empty()) {
                updated.tags = SizedCollection(tagsList)
            }
            else {
                val existing = updated.tags.toMutableList()
                existing.addAll(tagsList)
                val merged: SizedCollection<Tag> = SizedCollection(existing)
                updated.tags = merged
            }
        }
        return updated
    }

    fun update(bookId: UUID, book: BookCreateDto): Book {
        var found: Book = Book[bookId]
        return update(found, book)
    }

    fun update(userBookId: UUID, book: UserBookUpdateDto): UserBook {
        var found: UserBook = UserBook[userBookId]
        if (book.owned != null) {
            found.owned = book.owned
        }
        if (!book.personalNotes.isNullOrBlank()) {
            found.personalNotes = book.personalNotes
        }
        if (book.toRead != null) {
            found.toRead = book.toRead
        }
        if (book.percentRead != null) {
            found.percentRead = book.percentRead
        }
        if (book.book != null) {
            update(found.book, book.book)
        }
        if (book.lastReadingEvent != null) {
            readingEventRepository.save(found, CreateReadingEventDto(
                eventType = book.lastReadingEvent,
                bookId = null
            ))
        }
        return found
    }

    fun updateAuthor(authorId: UUID, author: AuthorUpdateDto): Author {
        val found: Author = Author[authorId]
        author.name?.run { found.name = author.name }
        found.modificationDate = nowInstant()
        return found
    }

    fun save(book: BookCreateDto): Book {
        //FIXME check if book with same isbn exists
        val authorsList = mutableListOf<Author>()
        book.authors?.forEach {
            val authorEntity: Author? = findAuthorsByName(it.name).firstOrNull()
            if (authorEntity != null) {
                authorsList.add(authorEntity)
            } else {
                authorsList.add(save(it))
            }
        }
        val tagsList = mutableListOf<Tag>()
        book.tags?.forEach {
            val tagEntity: Tag? = findTagsByName(it.name).firstOrNull()
            if (tagEntity != null) {
                tagsList.add(tagEntity)
            } else {
                tagsList.add(save(it))
            }
        }
        val created = Book.new{
            this.title = book.title
            val instant: Instant = nowInstant()
            this.creationDate = instant
            this.modificationDate = instant
            this.summary = book.summary
            this.isbn10 = book.isbn10
            this.isbn13 = book.isbn13
            this.pageCount = book.pageCount
            this.publishedDate = book.publishedDate
            this.publisher = book.publisher
            this.image = book.image
            this.series = book.series
            this.numberInSeries = book.numberInSeries
            this.amazonId = book.amazonId
            this.goodreadsId = book.goodreadsId
            this.googleId = book.googleId
            this.librarythingId = book.librarythingId
        }
        created.authors = SizedCollection(authorsList)
        created.tags = SizedCollection(tagsList)
        return created
    }

    fun save(author: AuthorDto): Author {
        val created = Author.new{
            name = author.name
            val instant: Instant = nowInstant()
            creationDate = instant
            modificationDate = instant
        }
        return created
    }

    fun save(tag: TagDto): Tag {
        val created = Tag.new{
            name = tag.name
            val instant: Instant = nowInstant()
            creationDate = instant
            modificationDate = instant
        }
        return created
    }

    fun save(book: Book, user: User, createUserBookDto: CreateUserBookDto): UserBook {
        val instant: Instant = nowInstant()
        return UserBook.new {
            this.creationDate = instant
            this.modificationDate = instant
            this.user = user
            this.book = book
            this.owned = createUserBookDto.owned
            this.toRead = createUserBookDto.toRead
            this.personalNotes = createUserBookDto.personalNotes
            this.percentRead = createUserBookDto.percentRead
        }
    }

    fun findUserBookById(userbookId: UUID): UserBook = UserBook[userbookId]

    fun findUserBookByCriteria(userID: EntityID<UUID>, searchTerm: ReadingEventType?, toRead: Boolean?): List<UserBook> {
        return UserBook.find {
            val userFilter: Op<Boolean> = UserBookTable.user eq userID
            val eventFilter: Op<Boolean> = if (searchTerm != null) {
                UserBookTable.lastReadingEvent eq searchTerm
            }
            else {
                Op.TRUE
            }
            val toReadFilter: Op<Boolean> = if (toRead != null) {
                UserBookTable.toRead eq toRead
            }
            else {
                Op.TRUE
            }
            userFilter and eventFilter and toReadFilter
        }.orderBy(Pair(UserBookTable.lastReadingEventDate, SortOrder.DESC_NULLS_LAST))
            .toList()
    }

    fun findUserBookByUserAndBook(user: User, book: Book): UserBook? {
        return UserBook.find { UserBookTable.user eq user.id and (UserBookTable.book.eq(book.id)) }.firstOrNull()
    }
}