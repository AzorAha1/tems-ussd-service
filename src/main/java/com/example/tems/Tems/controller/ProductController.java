// package com.example.tems.Tems.controller;

// import org.springframework.beans.factory.annotation.Autowired;
// import org.springframework.http.ResponseEntity;
// import org.springframework.web.bind.annotation.PostMapping;
// import org.springframework.web.bind.annotation.RequestMapping;
// import org.springframework.web.bind.annotation.RequestParam;
// import org.springframework.web.bind.annotation.RestController;

// import com.example.tems.Tems.service.ProductsService;

// manually creating a product everytime a phone number is received is not ideal for production, so for now, we will just create a product with a phone number and return the response
// @RestController
// @RequestMapping("/api/v1")
// public class ProductController {
//     @Autowired
//     private ProductsService productsService;
//     // This controller will handle product-related requests
//     // For example, creating a product, fetching products, etc.
//     // Add methods to handle product-related operations here
//     // Example method to create a product
//     @PostMapping("/create")
//     public ResponseEntity<String> createProduct(@RequestParam String phoneNumber) {
//         System.out.println("📞 Received phone number: " + phoneNumber);
//         String response = productsService.createTemsService(phoneNumber);
//         if (response.equals("Invalid phone number")) {
//             return ResponseEntity.badRequest().body("Invalid phone number provided.");
//         }
//         return ResponseEntity.ok(response);
//     }
// }

