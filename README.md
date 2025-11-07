# 📖 JPA API 성능 최적화 스터디 (jpashop)

이 프로젝트는 Spring Boot와 JPA를 기반으로 실무에서 API를 개발할 때, **지연 로딩(Lazy Loading)과 N+1 쿼리 문제**로 인해 발생하는 심각한 성능 저하를 식별하고, 이를 단계별로 해결해나가는 과정을 학습하는 스터디입니다.

단순히 엔티티를 조회하는 것을 넘어, API 응답 스펙(DTO)에 맞춰 데이터를 효율적으로 조회하는 다양한 최적화 전략과 그 트레이드오프를 비교 분석합니다.

## 📚 핵심 학습 주제

* 엔티티 직접 노출의 위험성과 `@JsonIgnore`의 한계
* **N+1 쿼리 문제**가 발생하는 원인과 식별 방법
* API 응답을 **DTO(Data Transfer Object)**로 변환하는 것의 중요성
* **페치 조인(Fetch Join)**을 활용한 N+1 문제 해결
* 컬렉션(OneToMany) 조회 시 페치 조인의 치명적 한계 (페이징 불가능)
* **`hibernate.default_batch_fetch_size`**를 이용한 컬렉션 조회 최적화 (실무 권장)
* JPA에서 DTO로 직접 조회하는 다양한 방법과 장단점 (V4, V5, V6)
* **OSIV (Open Session In View)**의 동작 방식과 실무적 고려사항
* **CQS (Command Query Separation)** 패턴을 통한 아키텍처 분리

---

## 🚀 API 성능 최적화 여정 (V1 ~ V6)

이 프로젝트는 `xToOne` 관계 조회와 `OneToMany` 컬렉션 조회라는 두 가지 핵심 시나리오를 통해 점진적으로 API 성능을 개선합니다.

### 1. xToOne (N:1, 1:1) 관계 조회 최적화

`Order`가 `Member`와 `Delivery`를 가지는 (N:1, 1:1) 관계에서의 성능 최적화 과정을 다룹니다.

* **V1: 엔티티 직접 노출 (`/api/v1/simple-orders`)**
    * **시도:** 가장 단순한 방식으로 `Order` 엔티티 리스트를 직접 반환합니다.
    * **고민 (문제):**
        1.  `LazyInitializationException` 발생: `Order`의 `Member`, `Delivery`가 지연 로딩 프록시 객체이므로 Jackson이 JSON으로 변환하지 못합니다. (`Hibernate5Module`로 해결 가능)
        2.  양방향 연관관계 무한 루프: `Order`와 `Member`가 서로를 참조하며 무한 루프에 빠집니다. (`@JsonIgnore`로 해결 필요)
        3.  **치명적 N+1 문제**: `Order` 조회 쿼리(1) 이후, 각 `Order`마다 `Member` 조회 쿼리(N), `Delivery` 조회 쿼리(N)가 추가로 발생합니다.

* **V2: 엔티티를 DTO로 변환 (`/api/v2/simple-orders`)**
    * **시도:** API 스펙에 맞춘 `SimpleOrderDto`를 도입하여 엔티티 의존성을 제거합니다.
    * **고민 (문제):** DTO 생성자 내부(`new SimpleOrderDto(o)`)에서 `order.getMember()`와 `order.getDelivery()`를 호출하며 지연 로딩이 발생합니다. V1과 마찬가지로 **N+1 쿼리 문제는 전혀 해결되지 않았습니다.**

* **V3: 페치 조인(Fetch Join)으로 최적화 (`/api/v3/simple-orders`)**
    * **시도:** `OrderRepository`에 `findAllWithMemberDelivery()` 메서드를 추가, JPQL의 `join fetch`를 사용합니다.
    * **해결:** `Order`를 조회하는 **단 1번의 SQL**로 `Member`와 `Delivery`의 실제 데이터까지 모두 가져옵니다. N+1 문제가 완벽하게 해결되며, 성능이 극적으로 향상됩니다.

* **V4: JPA에서 DTO로 바로 조회 (`/api/v4/simple-orders`)**
    * **시도:** `OrderSimpleQueryRepository`를 신설하여, JPQL의 `new` 키워드로 엔티티가 아닌 DTO를 즉시 반환합니다.
    * **고민 (장단점):**
        * **장점:** `SELECT` 절에서 원하는 필드만 선택(Projection)하므로 V3보다 네트워크 트래픽을 미세하게 최적화할 수 있습니다.
        * **단점:** DTO가 리포지토리 계층을 침범하여, 리포지토리의 재사용성이 떨어지고 API 스펙에 강하게 의존하게 됩니다.

> **[xToOne 결론]**
> **V3 (페치 조인)** 방식이 리포지토리 재사용성과 성능 최적화라는 두 마리 토끼를 잡는 가장 합리적인 방법입니다. V4는 성능 차이가 미미하고 유지보수가 어려워집니다.

---

### 2. OneToMany (1:N 컬렉션) 조회 최적화

`Order`가 `OrderItem` 리스트를 가지는 (1:N) 관계 조회 시 발생하는 더 심각한 성능 문제를 해결합니다.

* **V1: 엔티티 직접 노출 (`/api/v1/orders`)**
    * **고민 (문제):** V1(Simple)과 모든 문제를 공유하며, `Order` -> `OrderItem` -> `Item`으로 이어지는 연쇄적인 N+1 쿼리가 발생하여 **API 응답이 불가능한 수준**이 됩니다.

* **V2: 엔티티를 DTO로 변환 (`/api/v2/orders`)**
    * **시도:** `OrderDto` 내부에 `List<OrderItemDto>`를 포함시킵니다.
    * **고민 (문제):** `OrderDto`의 생성자에서 `order.getOrderItems()`를 호출(N+1 발생), `OrderItemDto`의 생성자에서 `orderItem.getItem()`을 호출(N*M의 N+1 발생)하며 **수많은 쿼리가 실행**됩니다.

* **V3: 페치 조인으로 최적화 (`/api/v3/orders`)**
    * **시도:** 컬렉션(`orderItems`)까지 모두 `join fetch`로 한 번에 조회합니다.
    * **고민 (한계):**
        1.  **데이터 중복(뻥튀기)**: 1:N 조인으로 인해 DB Row가 `Order` 기준이 아닌 `OrderItem` 기준으로 증가합니다. (`distinct`로 해결 필요)
        2.  **치명적 한계**: **페이징(Paging)이 불가능**합니다. JPA는 모든 데이터를 DB에서 읽어와 메모리에서 페이징 처리를 시도하므로, 데이터가 많으면 **OOM(Out Of Memory)으로 서버가 즉시 다운**될 수 있습니다.

* **V3.1: 페이징과 한계 돌파 (실무 최종 권장!) (`/api/v3.1/orders`)**
    * **시도:** V3의 페이징 문제를 해결하는 가장 실용적이고 강력한 방법입니다.
        1.  `ToOne` 관계(Member, Delivery)는 **페치 조인**으로 최적화합니다. (Row 수에 영향을 주지 않음)
        2.  `ToMany` 관계(OrderItems)는 `join fetch`를 제거하고 **지연 로딩**을 유지합니다.
        3.  `application.yml`에 **`hibernate.default_batch_fetch_size: 1000`** 설정을 추가합니다. (또는 `@BatchSize` 어노테이션 사용)
    * **해결:**
        1.  먼저 `Order`를 페이징하여 조회합니다. (쿼리 1번)
        2.  `OrderItems` 지연 로딩이 발생하면, `batch_fetch_size` 설정에 따라 `order_id IN (?, ?, ...)` 쿼리 **단 1번**으로 모든 `OrderItem`을 가져옵니다.
        3.  `Item` 지연 로딩도 마찬가지로 쿼리 1번으로 해결됩니다.
        4.  **결과: (1+N+M) 쿼리 -> (1+1+1) 수준으로 최적화되며, 페이징이 완벽하게 동작합니다!**

* **V4: JPA에서 DTO 직접 조회 (`/api/v4/orders`)**
    * **시도:** `OrderQueryRepository`를 사용, `findOrders()`로 루트 조회(1) 후, 루프를 돌며 `findOrderItems(orderId)`를 N번 호출합니다.
    * **고민 (문제):** V2와 동일하게 **1+N 쿼리 문제**가 발생합니다.

* **V5: DTO 직접 조회 (컬렉션 최적화) (`/api/v5/orders`)**
    * **시도:** V4를 개선하여, `findOrders()` (쿼리 1번) 실행 후, 조회된 모든 `orderId`를 리스트로 뽑아 `IN` 절을 사용하는 `findOrderItemMap()` (쿼리 1번)으로 모든 `OrderItem`을 가져옵니다.
    * **해결:** V3.1과 동일하게 **총 2번의 쿼리**로 해결됩니다. (메모리에서 `Map`을 통해 데이터 매칭)
    * **고민 (단점):** V3.1에 비해 리포지토리 코드가 훨씬 복잡해지고 유지보수가 어렵습니다.

* **V6: DTO 직접 조회 (플랫 데이터) (`/api/v6/orders`)**
    * **시도:** 모든 `JOIN` 결과를 평평한 DTO(`OrderFlatDto`)로 한 번에 조회합니다. (쿼리 1번)
    * **고민 (한계):**
        1.  애플리케이션에서 `groupingBy` 등을 통해 데이터를 수동으로 파싱하고 조립해야 합니다.
        2.  V3와 동일하게 데이터 중복 전송이 발생하며, **페이징이 불가능**합니다.

---

## 💡 실무 필수 최적화 (OSIV & CQS)

### 1. OSIV (Open Session In View)

* **고민:** `spring.jpa.open-in-view: true` (기본값)는 API 응답이 끝날 때까지 영속성 컨텍스트(DB 커넥션)를 유지시킵니다. 이는 컨트롤러에서도 지연 로딩이 가능하게 하여 편리해 보이지만...
* **문제:** 실시간 트래픽이 많은 API에서 DB 커넥션 풀이 빠르게 고갈되어 심각한 장애로 이어질 수 있습니다.
* **해결:** 실무에서는 **`spring.jpa.open-in-view: false`**로 설정하는 것을 권장합니다. 모든 지연 로딩은 반드시 트랜잭션(@Transactional) 안에서 완료되어야 합니다.

### 2. CQS (Command Query Separation)

* **고민:** OSIV를 끄면, 조회를 위한 로직도 모두 `@Transactional` 내부에서 처리해야 합니다. 이때 핵심 비즈니스 로직(Command)과 복잡한 조회 로직(Query)이 한 서비스에 섞여 복잡성이 증가합니다.
* **해결:** **서비스 계층을 분리**합니다.
    * **`OrderService` (Command):** 등록, 수정, 삭제 등 핵심 비즈니스 로직만 담당합니다.
    * **`OrderQueryService` (Query):** API 스펙에 맞춘 화면 종속적인 조회 로직(V3.1, V5 등)을 전담합니다. (`@Transactional(readOnly = true)` 사용)
    * 이를 통해 아키텍처가 명확해지고, 각자의 책임에만 집중하여 유지보수가 용이해집니다.

## 🏆 최종 권장 전략

1.  **우선 엔티티 조회 + DTO 변환(V2)**으로 개발합니다.
2.  성능 문제가 발생하면 **페치 조인(V3)**을 적용하여 N+1을 해결합니다.
3.  만약 **컬렉션 조회 + 페이징**이 필요하다면 **V3.1 (default_batch_fetch_size)**을 적용합니다.
    * *이 방식이 성능, 코드 복잡도, 유지보수성 모든 면에서 가장 균형 잡힌 실무적인 해결책입니다.*
4.  V3.1로도 성능이 부족한 특수한 경우(드물게 발생), **V5 (DTO 직접 조회 + IN절)**를 고려합니다.
