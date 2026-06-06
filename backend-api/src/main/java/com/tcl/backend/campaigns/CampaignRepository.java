package com.tcl.backend.campaigns;

import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

// Repository for reading/writing Campaign rows in the campaigns table.
// JpaRepository gives common methods like save, findById, findAll, and delete.
public interface CampaignRepository extends JpaRepository<Campaign, UUID> {

    // Custom JPQL query for public campaign browsing.
    // JPQL uses Java entity/field names (Campaign, c.status), not raw SQL table names.
    /*
     * This query looks like SQL, but it is JPQL.
     *
     * JPQL means Java Persistence Query Language. It queries Java entity classes
     * and Java field names instead of raw database table/column names.
     *
     * Java text block syntax:
     * """
     * ...
     * """
     * lets us write this query as a multi-line string.
     *
     * select c from Campaign c
     * means:
     * "Select Campaign objects. Inside this query, call each Campaign object c."
     *
     * In SQL we might write:
     * SELECT * FROM campaigns c
     *
     * In JPQL we write:
     * select c from Campaign c
     *
     * :status is a named parameter. It connects to:
     * @Param("status") CampaignStatus status
     *
     * (:status is null or c.status = :status)
     * means:
     * If status is null, ignore the status filter.
     * If status is not null, only return campaigns with that status.
     *
     * :q is another named parameter. It connects to:
     * @Param("q") String q
     *
     * (:q = '' or ...)
     * means:
     * If q is an empty string, ignore text search.
     * If q has text, search title or description.
     *
     * We use an empty string instead of null for q because PostgreSQL can treat a
     * null string parameter as bytea in the generated lower(...) expression.
     *
     * lower(c.title)
     * turns the campaign title lowercase.
     *
     * concat('%', :q, '%')
     * wraps the search text with % on both sides.
     * Example: q = "school" becomes "%school%".
     *
     * In LIKE queries, % means "anything before or after".
     * So LIKE "%school%" means "contains school".
     *
     * lower(c.title) like lower(concat('%', :q, '%'))
     * means:
     * title contains q, ignoring uppercase/lowercase.
     *
     * The full query means:
     * Return campaigns where status matches if provided,
     * and title/description contains q if provided.
     */
    @Query("""
            select c from Campaign c
            where (:status is null or c.status = :status)
              and (:q = ''
                   or lower(c.title) like lower(concat('%', :q, '%'))
                   or lower(c.description) like lower(concat('%', :q, '%')))
            """)
    // Returns a Spring Page so the API can return one page of campaign results at a time.
    Page<Campaign> search(
            // @Param connects this Java parameter to :status in the JPQL query.
            @Param("status") CampaignStatus status,
            // @Param connects this Java parameter to :q in the JPQL query.
            @Param("q") String q,
            // Pageable contains page number, page size, and sort order.
            Pageable pageable
    );
}
