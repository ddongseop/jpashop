package jpabook.jpashop.api;

import jpabook.jpashop.domain.Address;
import jpabook.jpashop.domain.Order;
import jpabook.jpashop.domain.OrderStatus;
import jpabook.jpashop.repository.OrderRepository;
import jpabook.jpashop.repository.OrderSearch;
import jpabook.jpashop.repository.order.simplequery.OrderSimpleQueryDto;
import jpabook.jpashop.repository.order.simplequery.OrderSimpleQueryRepository;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * xToOne (ManyToOne, OneToOne)
 * Order
 * Order -> Member (ManyToOne)
 * Order -> Delivery (OneToOne)
 */
@RestController
@RequiredArgsConstructor
public class OrderSimpleApiController {

    private final OrderRepository orderRepository;
    private final OrderSimpleQueryRepository orderSimpleQueryRepository;

    @GetMapping("/api/v1/simple-orders")
    public List<Order> ordersV1() {
        List<Order> all = orderRepository.findAllByString(new OrderSearch());
        for (Order order : all) {
            order.getMember().getName(); //LAZY 강제 초기화
            order.getDelivery().getAddress(); //LAZY 강제 초기화
        }
        return all;

        //문제1: JSON 생성 과정에서 양방향 연관관계 무한루프 발생, @JsonIgnore 로 해결
        //문제2: JSON 생성시 지연로딩으로 생성된 프록시 객체(Member)를 만났을 때 오류 발생,
        //      프록시는 그냥 무시하라는 의미로 Hibernate5Module 빈 등록
        //문제3: 전체 지연로딩 초기화 해버리면 필요 없는 애들도 같이 긁어와버림 (OrderItem 등),
        //      위의 코드처럼 필요한 객체들만 강제 초기화
        //--> 이렇게 해도 엔티티를 직접 노출했기 때문에 필요 없는 데이터들도 넘어감 (DTO 변환하기)
    }

    @GetMapping("/api/v2/simple-orders")
    public List<SimpleOrderDto> ordersV2() {
        //ORDER -> SQL 1번 -> 결과 주문수 2개 (N)
        //N + 1 -> 1 + 회원 N + 배송 N -> 최대 쿼리 5번 (영속성 컨텍스트에 있으면 생략)
        //EAGER로 하면 성능 최적화도 안되고, 예측이 안되는 쿼리가 날라감 (양방향 연관관계 등)
        return orderRepository.findAllByString(new OrderSearch()).stream()
                .map(o -> new SimpleOrderDto(o))
                .collect(Collectors.toList());
        //개선점: 엔티티가 직접 노출되지 않기 때문에 컴파일 과정에서 필드명 변경 등을 감지
    }

    @GetMapping("/api/v3/simple-orders")
    public List<SimpleOrderDto> ordersV3() {
        List<Order> orders = orderRepository.findAllWithMemberDelivery(); //LAZY 무시하고 페치 조인으로 가져오기
        return orders.stream()
                .map(o -> new SimpleOrderDto(o))
                .collect(Collectors.toList());
        //개선점: 페치 조인을 활용해 SQL이 1번만 나감
    }

    @GetMapping("/api/v4/simple-orders")
    public List<OrderSimpleQueryDto> ordersV4() {
        return orderSimpleQueryRepository.findOrderDtos();
        //개선점: 기존에 엔티티 조회 후에 DTO로 변환 했던 것을, 바로 DTO로 조회해버리기
        //--> 딱 내가 원하는 필드들만 끌고올 수 있지만 재사용성이 안좋음
    }

    @Data
    static class SimpleOrderDto {
        private Long orderId;
        private String name;
        private LocalDateTime orderDate;
        private OrderStatus orderStatus;
        private Address address;

        public SimpleOrderDto(Order order) {
            orderId = order.getId();
            name = order.getMember().getName(); //LAZY 초기화
            orderDate = order.getOrderDate();
            orderStatus = order.getStatus();
            address = order.getDelivery().getAddress(); //LAZY 초기화
        }
    }
}
