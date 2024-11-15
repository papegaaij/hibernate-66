package org.hibernate.entities;

import jakarta.persistence.*;

@Entity
public class AccessToken {

	@Id
	@Column
	@GeneratedValue
	private Long id;

	@ManyToOne(optional = false)
	@JoinColumn(nullable = false)
	private Account account;

	public Long getId() {
		return id;
	}

	public void setId(Long id) {
		this.id = id;
	}

	public Account getAccount() {
		return account;
	}

	public void setAccount(Account account) {
		this.account = account;
	}
}
