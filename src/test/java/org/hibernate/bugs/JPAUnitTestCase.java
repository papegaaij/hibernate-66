package org.hibernate.bugs;

import java.util.Random;
import java.util.function.BiConsumer;

import org.hibernate.entities.AccessToken;
import org.hibernate.entities.Account;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.Persistence;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaDelete;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Root;

/**
 * This template demonstrates how to develop a test case for Hibernate ORM,
 * using the Java Persistence API.
 */
class JPAUnitTestCase {

	private EntityManagerFactory entityManagerFactory;

	@BeforeEach
	void init() {
		entityManagerFactory = Persistence.createEntityManagerFactory("templatePU");
	}

	@AfterEach
	void destroy() {
		entityManagerFactory.close();
	}

	private void performShowcase(BiConsumer<EntityManager, Account> strategy) {
		try (EntityManager entityManager = entityManagerFactory.createEntityManager()) {

			// prep data
			entityManager.getTransaction().begin();

			final Account account = new Account();
			entityManager.persist(account);
			long accountId = account.getId();

			long minId = Long.MAX_VALUE;
			long maxId = Long.MIN_VALUE;
			for (int i = 0; i < 1000; i++) {
				final AccessToken token = new AccessToken();
				token.setAccount(account);
				entityManager.persist(token);
				minId = Math.min(minId, token.getId());
				maxId = Math.max(maxId, token.getId());
			}

			entityManager.getTransaction().commit();

			// Delete the entities
			entityManager.clear();
			entityManager.getTransaction().begin();

			// load some data, including some random tokens
			Account accountToRemove = entityManager.find(Account.class, accountId);
			for (int i = 0; i < 5; i++) {
				entityManager.find(AccessToken.class, minId + new Random().nextInt((int) (maxId - minId)));
			}

			strategy.accept(entityManager, accountToRemove);

			entityManager.getTransaction().commit();
		}
	}

	@Test
	void preHibernate66() throws Exception {
		performShowcase(this::preHibernate66Strategy);
	}

	/**
	 * This used to work prior to Hibernate 6.6. It does not require loading any
	 * additional entities in the entity manager.
	 */
	private void preHibernate66Strategy(EntityManager entityManager, Account accountToRemove) {
		CriteriaBuilder cb = entityManager.getCriteriaBuilder();
		CriteriaDelete<AccessToken> deleteStmt = cb.createCriteriaDelete(AccessToken.class);
		Root<AccessToken> root = deleteStmt.from(AccessToken.class);
		deleteStmt.where(cb.equal(root.get("account"), accountToRemove));
		entityManager.createQuery(deleteStmt).executeUpdate();

		entityManager.remove(accountToRemove);
	}

	@Test
	void clearEntityManager() throws Exception {
		performShowcase(this::clearEntityManagerStrategy);
	}

	/**
	 * Clearing the EntityManager prevents the TransientObjectException, but also
	 * has massive side effects on the code calling this method. The code calling
	 * this method must be aware that it cannot hold any references to entities when
	 * calling it.
	 */
	private void clearEntityManagerStrategy(EntityManager entityManager, Account accountToRemove) {
		CriteriaBuilder cb = entityManager.getCriteriaBuilder();
		CriteriaDelete<AccessToken> deleteStmt = cb.createCriteriaDelete(AccessToken.class);
		Root<AccessToken> root = deleteStmt.from(AccessToken.class);
		deleteStmt.where(cb.equal(root.get("account"), accountToRemove));
		entityManager.createQuery(deleteStmt).executeUpdate();

		entityManager.remove(accountToRemove);
		entityManager.clear();
	}

	@Test
	void fetchLoadDetach() throws Exception {
		performShowcase(this::fetchLoadDetachStrategy);
	}

	/**
	 * This strategy fetches the ids of the tokens to be removed and detaches all
	 * these from the EntityManager. This requires an additional query and a lot of
	 * work when processing thousands of tokens. It is however, the most efficient
	 * solution I could come up with for Hibernate 6.6.
	 */
	private void fetchLoadDetachStrategy(EntityManager entityManager, Account accountToRemove) {
		CriteriaBuilder cb = entityManager.getCriteriaBuilder();

		CriteriaQuery<Long> fetchIds = cb.createQuery(Long.class);
		Root<AccessToken> fetchRoot = fetchIds.from(AccessToken.class);
		fetchIds.select(fetchRoot.get("id")).where(cb.equal(fetchRoot.get("account"), accountToRemove));
		entityManager.createQuery(fetchIds).getResultList().forEach(id -> {
			AccessToken ref = entityManager.getReference(AccessToken.class, id);
			entityManager.detach(ref);
		});

		CriteriaDelete<AccessToken> deleteStmt = cb.createCriteriaDelete(AccessToken.class);
		Root<AccessToken> root = deleteStmt.from(AccessToken.class);
		deleteStmt.where(cb.equal(root.get("account"), accountToRemove));
		entityManager.createQuery(deleteStmt).executeUpdate();

		entityManager.remove(accountToRemove);
	}

	@Test
	void removeOneByOne() throws Exception {
		performShowcase(this::removeOneByOneStrategy);
	}

	/**
	 * This strategy fetches all tokens, attaches them and removes them via the
	 * EntityManager. This is the easiest solution, but also very inefficient.
	 */
	private void removeOneByOneStrategy(EntityManager entityManager, Account accountToRemove) {
		CriteriaBuilder cb = entityManager.getCriteriaBuilder();

		CriteriaQuery<AccessToken> fetchIds = cb.createQuery(AccessToken.class);
		Root<AccessToken> fetchRoot = fetchIds.from(AccessToken.class);
		fetchIds.where(cb.equal(fetchRoot.get("account"), accountToRemove));
		entityManager.createQuery(fetchIds).getResultList().forEach(entityManager::remove);

		entityManager.remove(accountToRemove);
	}
}
