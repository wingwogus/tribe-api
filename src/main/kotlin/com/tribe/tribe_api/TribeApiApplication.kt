package com.tribe.tribe_api

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.data.jpa.repository.config.EnableJpaAuditing

@SpringBootApplication
@EnableJpaAuditing
class TribeApiApplication

fun main(args: Array<String>) {
	runApplication<TribeApiApplication>(*args)
}
